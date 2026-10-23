package br.com.faru.boitata.core.draw

/**
 * Whether this device can blur what a layer draws (`BlurEffect`). Everywhere Skia draws (iOS, desktop, the web) it can; Android only from 12 (API 31). Where it cannot, a
 * blur is skipped and an effect should keep the rest of its look (a focus keeps its darkening).
 */
public expect fun blurSupported(): Boolean
