package com.openclassrooms.tourguide.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;

@Service
public class RewardsService {
	private static Logger logger = LoggerFactory.getLogger(Tracker.class);
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;
    private static Map<UUID, CompletableFuture> futurByUserId = new HashMap<>();
	// proximity in miles
    private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()*50);
	
	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}
	
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}
	
	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

	
	/**
	 * LA MISE EN PLACE DU CONTEXTE POUR POUVOIR CALCULER EN PARALLELE PLUSIEURS UTILISATEURS(EN CAS DE MULTIPLES APPELS EXTERNES) 
	 * méthode qui calcule les récompenses d'un utilisateur par appel de la méthode calculateRewardCore
	 * en lançant son éxécution en asynchrone
	 * @param user
	 */
	public void calculateRewards(User user) {
	    CompletableFuture futur = new CompletableFuture();
	    Set<String> rewardedAttractions = ConcurrentHashMap.newKeySet();
	    // Initialiser avec les attractions déjà récompensées, pour éviter les doublons
	    user.getUserRewards().forEach(r -> rewardedAttractions.add(r.attraction.attractionName)); 
	    futur = CompletableFuture.runAsync(() -> calculateRewardsCore(user, rewardedAttractions) , executor);
	    // le futur est stocké dans une map (IDuser, futur) pour suivre son traitement et savoir quand il a fini son traitement
	    futurByUserId.put(user.getUserId(), futur); // stocker le futur d'un User spécifique
	}
	
	
	
	/**
	 * LE CALCUL 
	 * méthode qui calcule les récompenses d'un utilisateur
	 * pour les dernieres attractions connues 
	 * @param user
	 * @param rewardedAttractions
	 */
	private void calculateRewardsCore(User user, Set<String> rewardedAttractions) {
    
		// je récupère toutes les endroits visités par l'utilisateur
	    List<VisitedLocation> userLocations = user.getVisitedLocations();
	    // je récupère toutes les attractions disponibles
	    List<Attraction> attractions = gpsUtil.getAttractions();
	    // je crée une liste concurrente pour stocker les récompenses. Plusieurs threads peuvent
	    //utiliser cette liste en même temps
	    ConcurrentLinkedQueue<UserReward> listeTemporaire = new ConcurrentLinkedQueue<>();

	    // pour chaque endroit visité, je récupère les attractions proches.
	    // Si l'utilisateur est proche d'une attraction, j'ajoute à la liste temporaire la récompense
	    userLocations.parallelStream().forEach(visitedLocation -> {
	        attractions.forEach(attraction -> {
	            if (nearAttraction(visitedLocation, attraction)) {
	                listeTemporaire.add(new UserReward(visitedLocation, attraction, 0));
	            }
	        });
	    });
	    // pour chaque récompense je vérifie que l'utilisateur ne la possède pas déjà
	    // si l'attraction n'est pas déjà présente, je calcule les points et ajoute ces points et la récompense dans le User
	    listeTemporaire.parallelStream().forEach(userReward -> {
	        if (rewardedAttractions.add(userReward.attraction.attractionName)) { // vrai si l'attraction n'était pas déjà présente
	            userReward.setRewardPoints(getRewardPoints(userReward.getAttraction(), user));
	            user.addUserReward(userReward);
	        }
	    });
	}
	/**
	 *Permet de savoir si le calcul de User.getUserReward est en cours ou non. 
	 * @param userId
	 */
	public static void attendLaFinDuCalculReward(UUID userId) {
		if(futurByUserId.containsKey(userId)) {// on cherche ce user dans la map
			try {
				futurByUserId.get(userId).get();
				// si on le trouve on attend que le calcul soit fini
			} catch (InterruptedException | ExecutionException e) {
				logger.debug("attendrefinCalculReward", e.toString());
				e.printStackTrace();
			}
		}
		// si on le trouve pas aucun calcul en cours, on n'attend pas
	}

	/**
	 * retoune VRAI si la distance entre la position de l'utilisateur et l'attraction est à moins de 200 miles
	 * @param attraction
	 * @param location
	 * @return
	 */
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return RewardsService.getDistance(attraction, location) > attractionProximityRange ? false : true;
	}
	/**
	 * retourne VRAI si la distance entre la position de l'utilisateur et l'attraction est a moins de 10miles
	 * @param visitedLocation
	 * @param attraction
	 * @return
	 */
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return RewardsService.getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
	}
	/**
	 * obtenir le nombre de point en fonction de la récompense
	 * @param attraction
	 * @param user
	 * @return
	 */
	private int getRewardPoints(Attraction attraction, User user) {
		int result = rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
		return result;
	}
	/**
	 * calcule la distance entre deux localisations. 
	 * @param loc1
	 * @param loc2
	 * @return
	 */
	public static double getDistance(Location loc1, Location loc2) {
        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);

        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                               + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

        double nauticalMiles = 60 * Math.toDegrees(angle);
        double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
        return statuteMiles;
	}

}
