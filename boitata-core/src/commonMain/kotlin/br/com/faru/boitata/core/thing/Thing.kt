package br.com.faru.boitata.core.thing

import br.com.faru.boitata.core.light.CasterKind
import br.com.faru.boitata.core.light.ObjectSize

/** A part of a thing's box, as shares of it: 0 at its left or top, 1 at its right or bottom. */
public data class Share(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    public companion object {
        /** The whole box. */
        public val Whole: Share = Share(0f, 0f, 1f, 1f)
    }
}

/**
 * One layer of a [Thing]: a picture ([picture], of whatever type the game draws with) laid at [box] in the thing's box, made of [material]. [depth] is how far behind the middle
 * of the thing's footprint the layer stands, in cm (a pot's back +4, its front -4; the inside of a mug behind its rim): it casts from there. [outline] is the picture its
 * shadow is cast from (a small solid silhouette, docs/art-pipeline.md), the picture itself when it has none; [normals] the normal map its highlights come from, if it has one.
 * A layer that gives light ([Material.emission]) neither casts nor receives light by default: it is the light.
 */
public class ThingLayer<P>(
    public val name: String,
    public val picture: P,
    public val material: Material,
    public val box: Share = Share.Whole,
    public val depth: Float = 0f,
    public val outline: P? = null,
    public val normals: P? = null,
    public val casts: Boolean = material.emission == null,
    public val receivesLight: Boolean = material.emission == null,
) {
    /** The picture its shadow is cast from. */
    public val castFrom: P get() = outline ?: picture

    /** How much of a light it stops: a glass chimney's shadow is faint, a tin's full. */
    public val opacity: Float get() = material.opacity
}

/**
 * A thing in a scene, as the engine needs to know it to draw it, light it and cast its shadows: its real [size] (cm; what is unknown is read from its pictures), how it meets
 * the room ([kind]: standing, lying flat, hung) and its [layers], back to front. A candle is its holder (brass), its wax and its flame, and the flame is the light; a potted plant
 * is its pot's back, its soil, its leaves and its pot's front, each with its own material. Plain data: the game builds one per thing and keeps it.
 */
public class Thing<P>(
    public val id: String,
    public val layers: List<ThingLayer<P>>,
    public val size: ObjectSize = ObjectSize(),
    public val kind: CasterKind = CasterKind.Standing,
) {
    init { require(layers.isNotEmpty()) { "$id: a thing has at least one layer" } }

    /** The layers that throw shadows. */
    public val casting: List<ThingLayer<P>> get() = layers.filter { it.casts }

    /** The layers that give light (a flame, a lit dial): where the thing's light comes from. */
    public val emitting: List<ThingLayer<P>> get() = layers.filter { it.material.emission != null }

    /** The layer called [name], if it has one. */
    public fun layer(name: String): ThingLayer<P>? = layers.firstOrNull { it.name == name }

    /** The thing's light, if a layer of it gives some: the strongest emitting layer's. */
    public val light: Emission? get() = emitting.maxByOrNull { it.material.emission!!.strength }?.material?.emission

    /**
     * How much of a light passing through the whole thing it stops, at most: what its darkest layer stops (a lantern of iron and glass stops it all where the iron is, less
     * where only glass is; per pixel the outlines say which).
     */
    public val opacity: Float get() = casting.maxOfOrNull { it.opacity } ?: 0f
}
