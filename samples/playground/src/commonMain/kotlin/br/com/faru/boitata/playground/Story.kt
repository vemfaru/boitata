package br.com.faru.boitata.playground

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.faru.boitata.audio.AudioSink
import br.com.faru.boitata.core.FastNoise
import br.com.faru.boitata.core.sky.MoonPhase
import br.com.faru.boitata.core.surface.PaneWeather
import br.com.faru.boitata.core.surface.SurfaceClimate
import br.com.faru.boitata.synth.Gunshot
import kotlinx.coroutines.flow.first
import kotlin.math.hypot

/** What the story's chapters need from the playground around them. */
internal class StoryEnv(val sink: AudioSink, val sound: Boolean, val moon: MoonPhase, val southern: Boolean)

/** A line of a verse or a note, in Portuguese and in English. */
internal class Bi(val pt: String, val en: String) { fun of(portuguese: Boolean) = if (portuguese) pt else en }

/**
 * One chapter of the cordel: its [verse] (four lines, one every few seconds), how long the story [runs] before it hands the scene over, what is on the screen while it runs ([story]), and what the viewer is given when it
 * pauses ([play]: the controls of the same scene, "Mexa na cena"), with a note on the physics behind them ([ficha]). [paperBurn] makes the way in a paper screen burning away instead of a fade.
 */
internal class Chapter(
    val title: Bi, val verse: List<Bi>, val runs: Float, val ficha: Bi, val paperBurn: Boolean = false,
    val story: @Composable (t: () -> Float, env: StoryEnv) -> Unit,
    val play: @Composable (t: () -> Float, env: StoryEnv) -> Unit,
)

private val StoryArt = listOf("ui_cover", "ui_ribbon", "ui_ribbon_small", "ui_ficha", "ui_button", "ui_progress", "ui_icon_play", "ui_icon_pause", "ui_icon_skip", "ui_icon_replay", "ui_icon_language", "theatre_paper") +
    (0..8).flatMap { listOf("ui_bookmark_${it}_on", "ui_bookmark_${it}_off") }

private fun bi(pt: String, en: String) = Bi(pt, en)

/** A physics note written in English first, then Portuguese. */
private fun note(en: String, pt: String) = Bi(pt, en)

/** A cold window: frost grows on the pane, fast. */
@Composable
private fun FrostWindow(t: () -> Float, env: StoryEnv) {
    val pane = remember { PaneWeather() }
    val climate = remember { SurfaceClimate().also { WindowWeather.Frost.applyTo(it) } }
    WindowScene(t, hour = 6.2f, cloud = 0.3f, moon = env.moon, southern = env.southern, windowOpen = false, pane = pane, climate = climate, weatherSpeed = 6f)
}

internal val Chapters: List<Chapter> = listOf(
    Chapter(bi("Teatro de Sombras", "Shadow Theatre"),
        listOf(bi("Antes do sol, antes do dia,", "Before the sun, before the day,"), bi("só a noite e a água fria.", "only night and cold water."), bi("Uma cobra comeu os olhos", "A snake ate the eyes"), bi("e a luz dentro dela ardia.", "and the light inside it burned.")),
        16f, note("A lamp behind paper: a shadow grows as the puppet nears the lamp and sharpens against the sheet. Size of the flame sets the softness.", "Uma lamparina atrás do papel: a sombra cresce perto da luz e afia junto ao papel. O tamanho da chama dá a maciez."),
        story = { t, _ -> ShadowTheatreScene(t, script = ::scriptedTheatre) }, play = { t, _ -> ShadowTheatreDemo(t) }),
    Chapter(bi("A Mata", "The Forest"),
        listOf(bi("Boitatá, cobra de fogo,", "Boitatá, serpent of fire,"), bi("guarda a mata de ponta a ponta.", "guards the forest end to end."), bi("Quem respeita, vê a luz;", "Who respects it sees the light;"), bi("quem destrói, a luz desconta.", "who destroys it, the light collects.")),
        18f, note("Depth layers move at their own speed (parallax); the serpent's fire is a light that moves, so every shadow in the wood swings with it.", "Camadas de profundidade andam em velocidades próprias (paralaxe); o fogo da serpente é uma luz que se move e balança todas as sombras."), paperBurn = true,
        story = { t, env -> ForestScene(t, rain = 0f, fog = 0.5f, moon = env.moon, southern = env.southern, hunter = false, wildlife = true) }, play = { t, env -> ForestDemo(t, env.sink, env.sound, env.moon, env.southern) }),
    Chapter(bi("O Fogo que Não Queima", "The Fire That Does Not Burn"),
        listOf(bi("Seu fogo não queima a folha,", "Its fire does not burn the leaf,"), bi("ilumina sem ferir;", "it lights without wounding;"), bi("mas o fogo do malvado", "but the wicked one's fire"), bi("ele sabe consumir.", "it knows how to consume.")),
        16f, note("Every material has an ignition heat and a way of burning. The guardian's fire spares living plants and puts out ordinary fires it touches.", "Todo material tem um calor de ignição e um jeito de queimar. O fogo guardião poupa as plantas vivas e apaga o fogo comum que toca."),
        story = { t, env -> BurnScene(t, guardian = true, timeScale = 2f, sink = env.sink, sound = env.sound) }, play = { t, env -> BurnDemo(t, env.sink, env.sound) }),
    Chapter(bi("O Caçador", "The Hunter"),
        listOf(bi("Veio um caçador de longe", "A hunter came from afar,"), bi("com fome de recompensa;", "hungry for reward;"), bi("pôs fogo no matagal", "he set the scrub alight"), bi("sem pensar na consequência.", "without thinking of the consequence.")),
        18f, note("Fire spreads downwind, from what is burning to what is dry beside it; rain wets things and puts it out. Dry grass catches first, a pine last.", "O fogo avança a favor do vento, do que queima para o que está seco ao lado; a chuva molha e apaga. O capim seco pega primeiro, o pinheiro por último."),
        story = { t, env -> BurnScene(t, guardian = false, timeScale = 3f, sink = env.sink, sound = env.sound) }, play = { t, env -> BurnDemo(t, env.sink, env.sound) }),
    Chapter(bi("A Noite de São João", "Saint John's Night"),
        listOf(bi("Era noite de São João,", "It was Saint John's night,"), bi("fogueira, balão e canção;", "bonfire, balloon and song;"), bi("Lampião e Maria Bonita", "Lampião and Maria Bonita"), bi("dançavam de coração.", "danced heart to heart.")),
        18f, note("Several lights at once: each throws its own shadows, and a shadow lands on whatever is behind it, bending onto walls.", "Várias luzes ao mesmo tempo: cada uma lança suas sombras, e a sombra cai no que estiver atrás, dobrando nas paredes."),
        story = { t, _ -> FestaScene(t) }, play = { t, _ -> FestaDemo(t) }),
    Chapter(bi("A Fuga", "The Flight"),
        listOf(bi("Correu mata, correu rio,", "He ran through forest, through river,"), bi("mas o fogo ia ao lado;", "but the fire ran beside him;"), bi("quem acendeu o que é mau", "who lit what is evil"), bi("sempre acaba cercado.", "always ends up surrounded.")),
        18f, note("The serpent circles in depth: far things are small and high, near things large and low, and the camera never moves.", "A serpente circula na profundidade: o longe é pequeno e alto, o perto grande e baixo, e a câmera não se move."),
        story = { t, _ -> ChaseScene(t) }, play = { t, _ -> ChaseDemo(t) }),
    Chapter(bi("A Lagoa", "The Lagoon"),
        listOf(bi("Entrou na lagoa escura", "He entered the dark lagoon"), bi("e a lagoa o refletiu;", "and the lagoon reflected him;"), bi("o fogo que ele acendera", "the fire he had lit"), bi("na água também se viu.", "was seen in the water too.")),
        18f, note("Water mirrors in proportion to its Fresnel reflectance, and what wades in looks nearer the surface than it is (Snell's law).", "A água reflete conforme a refletância de Fresnel, e o que entra parece mais perto da superfície do que está (lei de Snell)."),
        story = { t, env -> PondScene(t, hour = 22f, moon = env.moon, southern = env.southern) }, play = { t, env -> PondDemo(t, env.moon, env.southern) }),
    Chapter(bi("O Tiro", "The Shot"),
        listOf(bi("Quando a farda começou", "When his coat began"), bi("a arder sem piedade,", "to burn without mercy,"), bi("Lampião firmou a mira", "Lampião steadied his aim"), bi("e calou a crueldade.", "and silenced the cruelty.")),
        16f, note("A person is a material too: cloth chars, the body ends as ash that the wind carries away.", "Uma pessoa também é um material: o pano chamusca, o corpo vira cinza que o vento leva."),
        story = { t, env -> ShotScene(t, onShot = { if (env.sound) env.sink.play(Gunshot.render(0.06f), gain = 0.9f) }) }, play = { t, env -> ShotDemo(t) }),
    Chapter(bi("Madrugada", "Dawn"),
        listOf(bi("Amanheceu na serra fria,", "Dawn broke on the cold highlands,"), bi("geada no vidro, no chão;", "frost on the glass, on the ground;"), bi("o fogo bom da fogueira", "the good fire of the bonfire"), bi("e o fogo bom do coração.", "and the good fire of the heart.")),
        18f, note("Glass colder than the dew point fogs; below freezing the fog turns to frost that grows in from the edges.", "O vidro mais frio que o ponto de orvalho embaça; abaixo de zero vira geada que cresce das bordas."),
        story = { t, env -> FrostWindow(t, env) }, play = { t, env -> WindowDemo(t, env.moon, env.southern) }),
)

private enum class Mode { Cover, Story, Pause }

/**
 * O Fogo que Guarda: the library's scenes told as a cordel in nine chapters. Each chapter plays for a while with its verse on a ribbon, then pauses and hands the scene over ("Mexa na cena"): the controls of that scene,
 * with a note on the physics behind them. The first chapter's paper screen burns away into the second. [onWorkshop] opens the free playground (every tab).
 */
@Composable
internal fun StoryApp(seconds: () -> Float, sink: AudioSink, sound: Boolean, moon: MoonPhase, southern: Boolean, onWorkshop: () -> Unit, startAt: Int? = null, startPaused: Boolean = false) {
    val art = LocalSprites.current ?: return
    val loaded by produceState(false, art) { art.ensure(StoryArt); value = true }
    if (!loaded) return
    val env = StoryEnv(sink, sound, moon, southern)
    var mode by remember { mutableStateOf(if (startAt == null) Mode.Cover else if (startPaused) Mode.Pause else Mode.Story) }
    var chapter by remember { mutableIntStateOf(startAt ?: 0) }
    var startedAt by remember { mutableFloatStateOf(0f) }
    var restarts by remember { mutableIntStateOf(0) }
    var burnAt by remember { mutableFloatStateOf(if (startAt == 1 && !startPaused) 0f else -100f) }
    var portuguese by remember { mutableStateOf(true) }
    fun go(i: Int, burn: Boolean = false) { chapter = i.coerceIn(0, Chapters.lastIndex); mode = Mode.Story; startedAt = seconds(); restarts++; burnAt = if (burn) seconds() else -100f }
    // When the chapter has run its time, the story pauses and the scene is the viewer's.
    key(mode, chapter, restarts) {
        if (mode == Mode.Story) androidx.compose.runtime.LaunchedEffect(Unit) {
            snapshotFlow { seconds() - startedAt >= Chapters[chapter].runs }.first { it }
            mode = Mode.Pause
        }
    }
    val local: () -> Float = { seconds() - startedAt }
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0907))) {
        when (mode) {
            Mode.Cover -> Cover(art, portuguese, onStart = { go(0) }, onChapter = { go(it) }, onWorkshop = onWorkshop, onLanguage = { portuguese = !portuguese })
            Mode.Story -> key(chapter, restarts) {
                Box(Modifier.fillMaxSize()) {
                    Chapters[chapter].story(local, env)
                    if (Chapters[chapter].paperBurn) PaperBurn(art.art("theatre_paper"), { (seconds() - burnAt) / 4.5f })
                    Ribbon(art, Chapters[chapter], local, portuguese)
                }
            }
            Mode.Pause -> key(chapter) { Chapters[chapter].play(local, env) }
        }
        if (mode != Mode.Cover) TopBar(art, chapter, mode == Mode.Pause, portuguese, local,
            onCover = { mode = Mode.Cover }, onChapter = { go(it) }, onNext = { if (chapter < Chapters.lastIndex) go(chapter + 1, burn = chapter == 0) else mode = Mode.Cover },
            onReplay = { go(chapter) }, onLanguage = { portuguese = !portuguese }, onSkip = { mode = Mode.Pause })
        if (mode == Mode.Pause) Ficha(art, Chapters[chapter], portuguese, Modifier.align(Alignment.TopStart).padding(top = 76.dp, start = 12.dp))
    }
}

@Composable
private fun TopBar(art: Sprites, chapter: Int, paused: Boolean, portuguese: Boolean, t: () -> Float, onCover: () -> Unit, onChapter: (Int) -> Unit, onNext: () -> Unit, onReplay: () -> Unit, onLanguage: () -> Unit, onSkip: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0x990B0907)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("☰", Modifier.clickable { onCover() }.padding(8.dp), color = Color(0xFFF2D9A8), fontSize = 20.sp)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (i in Chapters.indices) {
                    val img = art.art("ui_bookmark_${i}_${if (i == chapter) "on" else "off"}")
                    Image(img, null, Modifier.weight(1f).height(24.dp).clickable { onChapter(i) }, contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                }
            }
            Chip(art, if (paused) (if (portuguese) "Continuar" else "Continue") else (if (portuguese) "Pular" else "Skip"), if (paused) onNext else onSkip)
            Chip(art, if (portuguese) "Rever" else "Replay", onReplay)
            Chip(art, if (portuguese) "EN" else "PT", onLanguage)
        }
        Spacer(Modifier.height(4.dp).fillMaxWidth().drawBehind {
            drawRect(Color(0x33F2D9A8)); drawRect(Color(0xFFF2A83B), size = Size(size.width * (if (paused) 1f else (t() / Chapters[chapter].runs).coerceIn(0f, 1f)), size.height))
        })
    }
}

@Composable
private fun Chip(art: Sprites, label: String, onClick: () -> Unit) {
    Box(Modifier.clickable { onClick() }, contentAlignment = Alignment.Center) {
        val img = art.art("ui_button")
        Image(img, null, Modifier.height(30.dp).width((30f * img.width / img.height).dp))
        Text(label, color = Color(0xFF2A1A0E), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** The verse on a paper ribbon at the foot of the screen: a line appears every few seconds, the Portuguese over its English. */
@Composable
private fun Ribbon(art: Sprites, chapter: Chapter, t: () -> Float, portuguese: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.fillMaxWidth(0.8f).padding(bottom = 10.dp).graphicsLayer { alpha = ((t() - 0.3f) * 2f).coerceIn(0f, 1f) * (1f - ((t() - (chapter.runs - 1f)) * 1f).coerceIn(0f, 1f)) }, contentAlignment = Alignment.Center) {
            val img = art.art("ui_ribbon")
            Image(img, null, Modifier.fillMaxWidth().height((0.8f * 0.0f).dp.coerceAtLeast(110.dp)))
            Column(Modifier.padding(horizontal = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                for ((i, line) in chapter.verse.withIndex()) {
                    Text(line.pt, Modifier.graphicsLayer { alpha = ((t() - (0.6f + i * 3f)) * 1.5f).coerceIn(0f, 1f) }, color = Color(0xFF2A1A0E), fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (!portuguese) Text(line.en, Modifier.graphicsLayer { alpha = ((t() - (0.6f + i * 3f)) * 1.5f).coerceIn(0f, 1f) }, color = Color(0xFF5A3A22), fontSize = 12.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** The note on a pinned slip: the one-line physics of what the viewer is about to move. */
@Composable
private fun Ficha(art: Sprites, chapter: Chapter, portuguese: Boolean, modifier: Modifier) {
    Box(modifier.width(320.dp), contentAlignment = Alignment.TopStart) {
        val img = art.art("ui_ficha")
        Image(img, null, Modifier.fillMaxWidth().height((320f * img.height / img.width).dp))
        Column(Modifier.padding(start = 26.dp, end = 120.dp, top = 30.dp)) {
            Text(if (portuguese) "Mexa na cena" else "Play with the scene", color = Color(0xFF2A1A0E), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(chapter.title.of(portuguese), color = Color(0xFF5A3A22), fontSize = 12.sp, fontStyle = FontStyle.Italic)
            Text(chapter.ficha.of(portuguese), color = Color(0xFF2A1A0E), fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}

/** The folheto: its cover, the chapters as bookmarks, and the door to the workshop. */
@Composable
private fun Cover(art: Sprites, portuguese: Boolean, onStart: () -> Unit, onChapter: (Int) -> Unit, onWorkshop: () -> Unit, onLanguage: () -> Unit) {
    Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
        val cover = art.art("ui_cover")
        Image(cover, null, Modifier.weight(1f).fillMaxSize().clickable { onStart() })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (portuguese) "Capítulos" else "Chapters", color = Color(0xFFF2D9A8), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            for (i in Chapters.indices) {
                Row(Modifier.clickable { onChapter(i) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val img = art.art("ui_bookmark_${i}_on")
                    Image(img, null, Modifier.height(24.dp).width((24f * img.width / img.height).dp))
                    Text(Chapters[i].title.of(portuguese), color = Color(0xFFF2D9A8), fontSize = 14.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(art, if (portuguese) "Começar" else "Begin", onStart)
                Chip(art, if (portuguese) "Oficina" else "Workshop", onWorkshop)
                Chip(art, if (portuguese) "EN" else "PT", onLanguage)
            }
        }
    }
}

/** A sheet of paper over the screen that catches fire at the bottom-left and burns away: the edge glows and chars, and what is behind shows through the hole. [progress] runs from 0 (whole sheet) to 1 (gone). */
@Composable
private fun PaperBurn(paper: androidx.compose.ui.graphics.ImageBitmap, progress: () -> Float) {
    Spacer(Modifier.fillMaxSize().drawBehind { drawPaperBurn(paper, progress().coerceIn(0f, 1.2f)) })
}

private val burnNoise = FastNoise(5L)

private fun DrawScope.drawPaperBurn(paper: androidx.compose.ui.graphics.ImageBitmap, p: Float) {
    if (p >= 1.15f) return
    val cols = 128; val rows = 72
    val cw = size.width / cols; val ch = size.height / rows
    val farthest = hypot(1f, 1f)
    fun reach(c: Int, r: Int): Float {
        val x = (c + 0.5f) / cols; val y = (r + 0.5f) / rows
        val d = hypot(x - 0.15f, y - 0.95f) / farthest
        return d * 0.9f + (burnNoise.at(c, r) * 0.5f + burnNoise.at(c / 4, r / 4 + 100) * 0.5f) * 0.14f
    }
    val front = p * 1.1f
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val f = reach(c, r)
            val x = c * cw; val y = r * ch
            when {
                f > front + 0.07f -> drawRect(Color(0xFFD9C79F), Offset(x, y), Size(cw + 0.6f, ch + 0.6f))
                f > front + 0.03f -> drawRect(Color(0xFF3B2412), Offset(x, y), Size(cw + 0.6f, ch + 0.6f))   // charring
                f > front -> drawRect(Color(0xFFFF8A2E).copy(alpha = 0.9f), Offset(x, y), Size(cw + 0.6f, ch + 0.6f))   // the glowing edge
                f > front - 0.025f -> drawRect(Color(0xFFFFD27A).copy(alpha = 0.35f), Offset(x, y), Size(cw + 0.6f, ch + 0.6f))
            }
        }
    }
    // The paper's fibres over what is left, the same as the theatre's screen.
    clipRect {
        val tile = size.width * 0.5f
        var yy = 0f
        while (yy < size.height) { var xx = 0f; while (xx < size.width) { drawArt(paper, Offset(xx, yy), tile / paper.width, Color(0xFF3A2A18).copy(alpha = 0.12f)); xx += tile }; yy += tile }
    }
}
