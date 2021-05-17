package org.ironica.amatsukaze

import amatsukazeGrammarLexer
import amatsukazeGrammarParser
import kotlinx.serialization.Serializable
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree

@Serializable
data class PlayerData(val id: Int, val x: Int, val y: Int, val dir: String, val role: String, val stamina: String)


@Serializable
data class Data(
    val type: String,
    val code: String,
    val grid: List<List<String>>,
    val layout: List<List<String>>,
    val colors: List<List<Color>>,
    val levels: List<List<Int>>,
    val portals: List<Portal>,
    val locks: List<Lock>,
    val players: List<PlayerData>
)

fun convertJsonToGrid(array: List<List<String>>): Grid {
    return array.map { it.map { when (it) {
        "OPEN" -> Block.OPEN
        "BLOCKED" -> Block.BLOCKED
        "WATER" -> Block.WATER
        "TREE" -> Block.TREE
        "DESERT" -> Block.DESERT
        "HOME" -> Block.HOME
        "MOUNTAIN" -> Block.MOUNTAIN
        "STONE" -> Block.STONE
        "LOCK" -> Block.LOCK
        else -> throw Exception("Cannot parse data to grid")
    } }.toMutableList() }.toMutableList()
}

fun convertJsonToLayout(array: List<List<String>>): Layout {
    return array.map { it.map { when (it) {
        "NONE" -> Item.NONE
        "GEM" -> Item.GEM
        "BEEPER" -> Item.BEEPER
        "OPENEDSWITCH" -> Item.OPENEDSWITCH
        "CLOSEDSWITCH" -> Item.CLOSEDSWITCH
        "PORTAL" -> Item.PORTAL
        "PLATFORM" -> Item.PLATFORM
        else -> throw Exception("Cannot parse data to layout")
    } }.toMutableList()}.toMutableList()
}

fun convertJsonToPlayers(array: List<PlayerData>): List<Player> {
    return array.map { when(it.role) {
        "PLAYER" -> Player(it.id, Coordinate(it.x, it.y), when(it.dir) {
            "UP" -> Direction.UP
            "DOWN" -> Direction.DOWN
            "LEFT" -> Direction.LEFT
            "RIGHT" -> Direction.RIGHT
            else -> throw Exception("Cannot parse data to player")
        }, it.stamina.toIntOrNull())
        "SPECIALIST" -> Specialist(it.id, Coordinate(it.x, it.y), when(it.dir) {
            "UP" -> Direction.UP
            "DOWN" -> Direction.DOWN
            "LEFT" -> Direction.LEFT
            "RIGHT" -> Direction.RIGHT
            else -> throw Exception("Cannot parse data to specialist")
        }, it.stamina.toIntOrNull())
        else -> throw Exception("Cannot parse data to player list")
    } }
}

fun convertJsonToMiscLayout(colors: List<List<Color>>, levels: List<List<Int>>, using: String, defaultSize: Pair<Int, Int>): SecondLayout {
    return when (using) {
        "colorful" -> {
            if (colors.size == defaultSize.first && colors[0].size == defaultSize.second)
                colors.map { it.map { ColorfulTile(it) as Tile }.toMutableList() }.toMutableList()
            else
                MutableList(defaultSize.first) { MutableList(defaultSize.second) { ColorfulTile(Color.WHITE) } }
        }
        "mountainous" -> {
            if (levels.size == defaultSize.first && levels[0].size == defaultSize.second)
                levels.map { it.map { MountainTile(it) as Tile }.toMutableList() }.toMutableList()
            else
                MutableList(defaultSize.first) { MutableList(defaultSize.second) { MountainTile(1) } }
        }
        "colorfulmountainous" -> {
            val cc = if (colors.size == defaultSize.first && colors[0].size == defaultSize.second) colors
            else List(defaultSize.first) { List(defaultSize.second) { Color.WHITE } }
            val ll = if (levels.size == defaultSize.first && levels[0].size == defaultSize.second) levels
            else List(defaultSize.first) { List(defaultSize.second) { 1 } }

            cc.mapIndexed { i, line ->
                line.mapIndexed { j, it -> ColorfulMountainTile(it, ll[i][j]) as Tile }.toMutableList()
            }.toMutableList()
        }
        else -> throw Exception("Unsupported game module")
    }
}

private fun convertDataToColor(data: String): Color {
    return when (data) {
        "BLACK" -> Color.BLACK
        "SILVER" -> Color.SILVER
        "GREY" -> Color.GREY
        "WHITE" -> Color.WHITE
        "RED" -> Color.RED
        "ORANGE" -> Color.ORANGE
        "GOLD" -> Color.GOLD
        "PINK" -> Color.PINK
        "YELLOW" -> Color.YELLOW
        "BEIGE" -> Color.BEIGE
        "BROWN" -> Color.BROWN
        "GREEN" -> Color.GREEN
        "AZURE" -> Color.AZURE
        "CYAN" -> Color.CYAN
        "ALICEBLUE" -> Color.ALICEBLUE
        "PURPLE" -> Color.PURPLE
        else -> throw Exception("Cannot parse data to color")
    }
}

fun calculateInitialGem(layout: Layout): Int = layout.flatten().filter { it == Item.GEM }.size

class AmatsukazeInterface(
    val type: String,
    val code: String,
    val grid: Grid,
    val layout: Layout,
    val miscLayout: SecondLayout,
    val portals: List<Portal>,
    val locks: List<Lock>,
    val players: List<Player>,
    val debug: Boolean, val stdout: Boolean
) {
    fun start() {
        val input: CharStream = CharStreams.fromString(code)
        val lexer = amatsukazeGrammarLexer(input)
        val tokens = CommonTokenStream(lexer)
        val parser = amatsukazeGrammarParser(tokens)
        val tree: ParseTree = parser.top_level()
        val playground = Playground(grid, layout, miscLayout, portals, locks, players.toMutableList(), calculateInitialGem(layout))
        val manager = when (type) {
            "colorful" -> ColorfulManager(playground, debug, stdout)
            "mountainous" -> MountainousManager(playground, debug, stdout)
            "colorfulmountainous" -> ColorfulMountainManager(playground, debug, stdout)
            else -> throw Exception("Unsupported game module")
        }
        manager.appendEntry() // Store the initial state of playground into payload list
        val exec = AmatsukazeVisitor(manager)
        exec.visit(tree)
    }
}