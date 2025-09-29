package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;

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

	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	public VisitedLocation getUserLocation(User user) {
		VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation()
				: trackUserLocation(user);
		return visitedLocation;
	}

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return internalUserMap.values().stream().collect(Collectors.toList());
	}

	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}
	
	public VisitedLocation trackUserLocation(User user) {
	    try {
	        // Appel à la méthode asynchrone et attente du résultat
	        return trackUserLocationAsync(user).get();
	    } catch (InterruptedException e) {
	        // Restauration de l'état d'interruption du thread
	        Thread.currentThread().interrupt();
	        // Vous pouvez logger l'erreur ici
	        throw new RuntimeException("Le thread a été interrompu lors du tracking de l'utilisateur", e);
	    } catch (ExecutionException e) {
	        // Gestion de l'exception qui a causé l'échec de l'exécution asynchrone
	        Throwable cause = e.getCause();
	        // Vous pouvez logger le détail de la cause ici
	        throw new RuntimeException("Erreur lors du tracking de l'utilisateur", cause);
	    }
	}
	
	
	public CompletableFuture<VisitedLocation> trackUserLocationAsync(User user) {
	    /*return CompletableFuture.supplyAsync(() -> {
	    	// utilisation d'un COmpletableFutur pour lancer le traitement en asynchrone
	        VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
	        user.addToVisitedLocations(visitedLocation);
	        rewardsService.calculateRewards(user);
	        for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
	            System.out.println(ste);
	        }
	        return visitedLocation;
	    });
	    */
	    	// utilisation d'un COmpletableFutur pour lancer le traitement en asynchrone
			Long debut = System.currentTimeMillis();//CLA
	        VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
	        System.out.println("getUserLocation 1 Temps écoulé (ms) : " + (System.currentTimeMillis() - debut));//CLA
	        debut = System.currentTimeMillis();//CLA
	        user.addToVisitedLocations(visitedLocation);
	        System.out.println("addToVisitedLocation 2 Temps écoulé (ms) : " + (System.currentTimeMillis() - debut));//CLA
	        debut = System.currentTimeMillis();//CLA
	        rewardsService.calculateRewards(user);
	        System.out.println("calculatereward 3 Temps écoulé (ms) : " + (System.currentTimeMillis() - debut));//CLA
	        return CompletableFuture.supplyAsync(() -> { return visitedLocation;
	    });
	}
	public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
	    List<Attraction> originalList = gpsUtil.getAttractions();
	    // Copie locale pour éviter les effets de bord liés au multi-threading
	    List<Attraction> copieAttractions = new ArrayList<>(originalList);
        // on crée un comparateur pour comparer les attractions et ainsi pouvoir les trier 
	    Comparator<Attraction> distanceComparator =
	            (a1, a2) -> Double.compare(rewardsService.getDistance(a1, visitedLocation.location), 
	            		rewardsService.getDistance(a2, visitedLocation.location));
	            // double est privilégié a int pour éviter les erreurs d'arrondi

	    // on remplie la liste des 5 attractions les plus proches de l'utilisateur 
	    List<Attraction> nearbyAttractions = copieAttractions.stream()
	    	    .sorted(distanceComparator) // on trie la liste du plus proche au plus lointain
	    	    .limit(5) // dans la limite de 5 attractions
	    	    .collect(Collectors.toList()); // qu'on insère dans la liste

	    return nearbyAttractions;
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
	private final Map<String, User> internalUserMap = new HashMap<>();

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
