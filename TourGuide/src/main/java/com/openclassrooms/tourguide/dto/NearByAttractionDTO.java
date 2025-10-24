package com.openclassrooms.tourguide.dto;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

public class NearByAttractionDTO {
	 //  TODO: Change this method to no longer return a List of Attractions.
 	//  Instead: Get the closest five tourist attractions to the user - no matter how far away they are.
 	//  Return a new JSON object that contains:
    	// Name of Tourist attraction, 
        // Tourist attractions lat/long, 
        // The user's location lat/long, 
        // The distance in miles between the user's location and each of the attractions.
        // The reward points for visiting each Attraction.
        //    Note: Attraction reward points can be gathered from RewardsCentral

	private Location userLocation;
	private List<AttractionInformationDTO> attractionInformation;
	
	/**
	 * constructeur par défaut de NearByAttractionDTO pour construire l'objet
	 */
	public NearByAttractionDTO() {
	}
	
	public Location getUserLocation() {
		return userLocation;
	}

	public void setUserLocation(Location userLocation) {
		this.userLocation = userLocation;
	}

	public List<AttractionInformationDTO> getAttractionInformation() {
		return attractionInformation;
	}

	public void setAttractionInformation(List<AttractionInformationDTO> attractionInformation) {
		this.attractionInformation = attractionInformation;
	}

	/**
	 * transcrit un objet NearAttractionDTO en string au format Json 
	 * @return
	 */
	public String toJson() {
		ObjectMapper mapper = new ObjectMapper();
		String json = "";
		try {
			json = mapper.writeValueAsString(this);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return json;
	}

	

}
