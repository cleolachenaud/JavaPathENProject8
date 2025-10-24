package com.openclassrooms.tourguide.dto;

import com.openclassrooms.tourguide.service.RewardsService;

import gpsUtil.location.Location;

public class AttractionInformationDTO {
	private String attractionName;
	private Location attractionLocation;
	private double distanceUserAttraction;
	private Integer nbPoints;
	
	
	public AttractionInformationDTO(String attractionName, Location attractionLocation, Integer nbPoints, Location currentUserLocation) {
		super();
		this.attractionName = attractionName;
		this.attractionLocation = attractionLocation;
		this.nbPoints = nbPoints;
		this.setDistanceUserAttraction(currentUserLocation);
	}
	public String getAttractionName() {
		return attractionName;
	}
	public void setAttractionName(String attractionName) {
		this.attractionName = attractionName;
	}
	public Location getAttractionLocation() {
		return attractionLocation;
	}
	public void setAttractionLocation(Location attractionLocation) {
		this.attractionLocation = attractionLocation;
	}
	public double getDistanceUserAttraction() {
		return distanceUserAttraction;
	}
	public void setDistanceUserAttraction(Location currentUserLocation) {
		this.distanceUserAttraction = RewardsService.getDistance(this.attractionLocation, currentUserLocation);
	}
	public Integer getNbPoints() {
		return nbPoints;
	}
	public void setNbPoints(Integer nbPoints) {
		this.nbPoints = nbPoints;
	}
	
	
}