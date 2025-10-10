package com.openclassrooms.tourguide.utils;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class FuturUtils {
	/**
	 * classe qui permet de récupérer un futur et son temps d'actualisation
	 */
	private Long timeDebut; 
	private CompletableFuture<?> futur;
	
	public FuturUtils(Long timeDebut, CompletableFuture<?> futur) {
		this.timeDebut = timeDebut;
		this.futur = futur;
	}
	public FuturUtils(CompletableFuture<?> futur) {
		this (System.currentTimeMillis(),futur);
	}

	public Long getTimeDebut() {
		return timeDebut;
	}

	public CompletableFuture<?> getFutur() {
		return futur;
	}


}
