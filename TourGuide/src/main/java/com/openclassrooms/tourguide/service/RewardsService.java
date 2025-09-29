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
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

import org.apache.commons.lang3.time.StopWatch;
import org.springframework.stereotype.Service;

import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;

@Service
public class RewardsService {
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;
    private static Map<UUID, CompletableFuture> futurManagement = new HashMap<>();
	// proximity in miles
    private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	
	
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
	
	public void calculateRewards(User user) {
	    List<VisitedLocation> userLocations = user.getVisitedLocations();
	    List<Attraction> attractions = gpsUtil.getAttractions();
	    // création d'une variable CompletableFutur pour différer l'execution
	    CompletableFuture futur = new CompletableFuture();
	    ConcurrentLinkedQueue<UserReward> listeTemporaire = new ConcurrentLinkedQueue<>();

	    userLocations.parallelStream().forEach(visitedLocation -> {
	        attractions.forEach(attraction -> {
	            if (nearAttraction(visitedLocation, attraction)) {
	                listeTemporaire.add(new UserReward(visitedLocation, attraction, 0));
	            }
	        });
	    });

	    Set<String> rewardedAttractions = ConcurrentHashMap.newKeySet();
	    // Initialiser avec les attractions déjà récompensées
	    user.getUserRewards().forEach(r -> rewardedAttractions.add(r.attraction.attractionName));

	    futur = CompletableFuture.runAsync(() ->
	    listeTemporaire.parallelStream().forEach(userReward -> {
	        if (rewardedAttractions.add(userReward.attraction.attractionName)) { // vrai si l'attraction n'était pas déjà présente
	            userReward.setRewardPoints(getRewardPoints(userReward.getAttraction(), user));
	            user.addUserReward(userReward);
	        }
	    }) , executor);
	    futurManagement.put(user.getUserId(), futur); // stocker le futur d'un User spécifique
	}
/**
 * méthode qui permet de savoir si le calcul des récompenses pour un user passé en paramètre est
 * terminé ou non. 
 * @param userId
 */
	public static void attendLaFinDuCalculReward(UUID userId) {
		if(futurManagement.containsKey(userId)) {// on cherche ce user dans la map
			try {
				futurManagement.get(userId).get();
				// si on le trouve on attend que le calcul soit fini
			} catch (InterruptedException | ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		// si on le trouve pas aucun calcul en cours, on n'attend pas
	}
	private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()*50);
	
	
	// retient toutes les attractions à proximité (moins de 200miles) 
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) > attractionProximityRange ? false : true;
	}
	
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
	}
	
	private int getRewardPoints(Attraction attraction, User user) {
		//StopWatch stopWatch = new StopWatch();
		//stopWatch.start();
		int result = rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
		//stopWatch.stop();
		//System.out.println("getRewardPoints > rewardsCentral.getAttractionRewardPoints " + stopWatch.getTime() + "ms. " + attraction.attractionName + " - " + attraction.attractionId + " (" + user.getUserName() + ") ==> " + result);
		return result;
	}
	
	public double getDistance(Location loc1, Location loc2) {
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
