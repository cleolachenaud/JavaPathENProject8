package com.openclassrooms.tourguide.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.openclassrooms.tourguide.dto.AttractionInformationDTO;
import com.openclassrooms.tourguide.dto.NearByAttractionDTO;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import com.openclassrooms.tourguide.utils.FuturUtils;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {
	private static Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;
	private final static Long TIME21 = TimeUnit.MINUTES.toMillis(21L); // pour demander le rafraichissement au bout de 21 minutes
	private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()*50);
	private final Map<String, User> internalUserMap = new HashMap<>();
	
	private static Map<UUID, FuturUtils> futurByUserId = new HashMap<>();
	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;
		
		Locale.setDefault(Locale.US);

		if (testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		tracker = new Tracker(this);
		addShutDownHook();
	}
	/**
	 * retourne la liste des récompenses de l'utilisateur transmis en paramètre 
	 * @param user
	 * @return
	 */
	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	/**
	 * retourne la position de l'utilisateur 
	 * @param user
	 * @return
	 */
	public VisitedLocation getUserLocation(User user) {
		VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation()
				: trackUserLocation(user);
		return visitedLocation;
	}
	/**
	 * retourne le User a partir de son Username
	 * @param userName
	 * @return
	 */
	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}
	/**
	 * retourne une liste de tous les Users
	 * @return
	 */
	public List<User> getAllUsers() {
		return internalUserMap.values().stream().collect(Collectors.toList());
	}
	/**
	 * ajoute le User transmis en paramètre aux données internes de la classe 
	 * @param user
	 */
	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	/**
	 * Retourne une liste de fournisseurs de services avec prix dedans en fonction des préférences du User passé en paramètre
	 * @param user
	 * @return
	 */
	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}
	

	
	/**
	 *  récupère les endroits visités par le user passé en paramètre, 
	 *  ajoute le dernier endroit visité et calcule ses récompenses
	 * @param user
	 */
	private void trackUserLocationCore(User user) {
        VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
        user.addToVisitedLocations(visitedLocation);
        rewardsService.calculateRewards(user);
	}
	/**
	 * LA MISE EN PLACE DU CONTEXTE POUR POUVOIR CALCULER EN PARALLELE PLUSIEURS UTILISATEURS(EN CAS DE MULTIPLES APPELS EXTERNES) 
	 * @param user
	 */
	public void getUserLocationAsync(User user) {
		CompletableFuture futur = CompletableFuture.runAsync(() -> trackUserLocationCore(user), executor);
		FuturUtils futurUtils = new FuturUtils(futur);
		futurByUserId.put(user.getUserId(), futurUtils); // je stock ici l'ID de mon user + l'heure à laquel le traitement est effectué ainsi que le traitement 
	}
	
	/**
	 * retourne le dernier endroit visité. 
	 * En vérifiant que le calcul du dernier endroit visité est bien terminé (cf attendLaFinDuCalculGetUserLocation)
	 * @param user
	 * @return
	 */
	public VisitedLocation trackUserLocation(User user) {
		attendLaFinDuCalculGetUserLocation(user);
        return user.getLastVisitedLocation();
}
	/**
	 * permet de savoir si le calcul de GetUserLocation est en cours ou non. 
	 * La méthode relance le calcul manuellement dans le cas ou il n'y a pas de traitement pour ce user ou que celui ci date de plus de 21 minutes
	 * @param user
	 */
	private void attendLaFinDuCalculGetUserLocation(User user) {
		FuturUtils futurUtils = futurByUserId.get(user.getUserId()); // je récupère le futur et le temps d'actualisation lié au user 
		if(!(futurUtils != null && (Long) futurUtils.getTimeDebut()+TIME21 > System.currentTimeMillis())) {
			// si je n'ai pas de traitement pour ce user OU que le traitement a été lancé il y a plus de 21 minutes
			// je n'ai pas encore les informations, ou elles sont expirées, je lance mon calcul manuellement 
			getUserLocationAsync(user);
		}
		// puis je récupère le futur terminé
		try {
			futurByUserId.get(user.getUserId()).getFutur().get();
		} catch (InterruptedException | ExecutionException e) {
			logger.debug("attendrefinGetUserLocation", e.toString());
			e.printStackTrace();
		}
	
	}
	/**
	 * retourne les 5 attractions les plus proche de l'utilisateur 
	 * @param visitedLocation
	 * @return
	 */
	public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
	    List<Attraction> originalList = gpsUtil.getAttractions();
	    // Copie locale pour éviter les effets de bord liés au multi-threading
	    ArrayList<Attraction> copieAttractions = new ArrayList<>(originalList);
        // on crée un comparateur pour comparer les attractions et ainsi pouvoir les trier 
	    Comparator<Attraction> distanceComparator =
	            (a1, a2) -> Double.compare(RewardsService.getDistance(a1, visitedLocation.location), 
	            		RewardsService.getDistance(a2, visitedLocation.location));
	            // double pour éviter les erreurs d'arrondi

	    // on remplie la liste des 5 attractions les plus proches de l'utilisateur 
	    List<Attraction> nearbyAttractions = copieAttractions.stream()
	    	    .sorted(distanceComparator) // on trie la liste du plus proche au plus lointain
	    	    .limit(5) // dans la limite de 5 attractions
	    	    .collect(Collectors.toList()); // qu'on insère dans la liste
	   
	    return nearbyAttractions;
	}
	/**
	 * retourne les 5 attractions les plus proche de l'utilisateur 
	 * @param visitedLocation
	 * @return
	 */
	public String getNearByAttractionsAsJson(String userName) {
    	NearByAttractionDTO nearByAttractionDTO = new NearByAttractionDTO();
    	RewardCentral rewardCentral = new RewardCentral();
    	User user = getUser(userName);
    	VisitedLocation visitedLocation = this.getUserLocation(user);
    	nearByAttractionDTO.setUserLocation(visitedLocation.location);
    	List<AttractionInformationDTO> listAttractionInformationDTO = new ArrayList<>();
    	for (Attraction attraction : this.getNearByAttractions(visitedLocation)) {
    		Location attractionLocation= new Location(attraction.latitude, attraction.longitude);
        	Integer nbPoint = rewardCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
    		AttractionInformationDTO attractionInformationDTO = new AttractionInformationDTO(attraction.attractionName, attractionLocation, nbPoint, user.getLastVisitedLocation().location);
    		listAttractionInformationDTO.add(attractionInformationDTO);
    	}
    	nearByAttractionDTO.setAttractionInformation(listAttractionInformationDTO);

    	return nearByAttractionDTO.toJson();
	}
	

	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				tracker.stopTracking();
			}
		});
	}
//TODO repasser ça dans des tests c'est pas possible 
	/**********************************************************************************
	 * 
	 * Methods Below: For Internal Testing
	 * 
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes
	// internal users are provided and stored in memory
	

	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);

			internalUserMap.put(userName, user);
		});
		logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}

	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i -> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
					new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

}
