package com.openclassrooms.tourguide.dto;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

public class NearByAttractionDTO {
/**
 * classe qui permet de créer une liste d'"attractionInformationDTO" et qui récupère la position du User
 */
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
