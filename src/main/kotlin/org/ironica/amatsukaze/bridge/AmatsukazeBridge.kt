/*
 * Copyright (c) 2020-2021. kokoro-aya. All right reserved.
 * Amatsukaze - A Playground Server implemented with ANTLR or Kotlin DSL
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.ironica.amatsukaze.bridge

import amatsukazeGrammarLexer
import amatsukazeGrammarParser
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import org.ironica.amatsukaze.corelanguage.AmatsukazeVisitor
import org.ironica.amatsukaze.manager.ColorfulManager
import org.ironica.amatsukaze.manager.ColorfulMountainManager
import org.ironica.amatsukaze.manager.MountainousManager
import org.ironica.amatsukaze.playground.*
import org.ironica.amatsukaze.playground.data.*
import org.ironica.amatsukaze.playground.characters.Player
import utils.zip

class AmatsukazeBridge(
    val type: String,
    val code: String,
    val grid: Grid,
    val itemLayout: ItemLayout,
    val colors: List<List<Color>>,
    val levels: List<List<Int>>,
    val biomes: List<List<Biome>>,
    val portaldatas: List<PortalData>,
    val lockdatas: List<LockData>,
    val stairdatas: List<StairData>,
    val platformdatas: List<PlatformData>,
    val playerdatas: List<PlayerData>,
    val debug: Boolean, val stdout: Boolean,

    var squares: List<List<Square>>? = null, // TODO need to improve this
    var locks: Map<Lock, Coordinate>? = null,
    var portals: Map<Portal, Coordinate>? = null,
    var players: Map<Player, Coordinate>? = null,
) {
    // Pre-initialization of data structures to be passed to playground
    // = Pre-init Checks
    //   - Dimension check -
    //   - Check every lock block corresponds to a lock* registration and vice versa -
    //   - Check every platform item corresponds to a platform registration and vice versa -
    //   - Check every controlled tiles in lock has a platform (platformdata will be consumed) -
    //   - Check every portal item corresponds to a portal registration and vice versa -
    //   - Check every stairs tiles corresponds to a stair* registration and vice versa -
    // = Pre-initialization phase
    // - prepare portals, locks and players data structures for playground -
    // - Convert grid to 2d block array (lockdata and stairdata will be consumed) -
    // - Integrate arrays of block layout and misc layout to square arrays -
    // - Assign items to square arrays (portaldata will be consumed) -
    // - Assign players to corresponding squares(playerdata will be consumed) -

    // *: this will be delayed into the loop of squares creation
    init {
        preInitChecks()

        portals = portaldatas.associate { Portal(it.coo, it.dest, isActive = true, color = Color.WHITE) to it.coo }
        // TODO add colors on portals
        locks = lockdatas.associate { Lock(it.controlled.toMutableList()) to it.coo }

        players = playerdatas.associate { Player(it.id, it.dir, it.stamina) to Coordinate(it.x, it.y) }

        val platforms = platformdatas.associate { Platform(it.level) to it.coo }

        var homeId = 0
        val blocks = grid.mapIndexed { i, line ->
            line.mapIndexed { j, b ->
                when (b) {
                    Blocks.OPEN -> Open
                    Blocks.HILL -> Hill
                    Blocks.WATER -> Water
                    Blocks.TREE -> Tree
                    Blocks.DESERT -> Desert
                    Blocks.HOME -> Home(homeId++)
                    Blocks.MOUNTAIN -> Mountain
                    Blocks.STONE -> Stone
                    Blocks.LOCK -> locks!!.entries.firstOrNull { it.value == Coordinate(j, i) }?.key
                        ?: throw Exception("Initialization:: A tile declared as Lock without lock info registered")
                    Blocks.STAIR -> stairdatas.firstOrNull { it.coo == Coordinate(j, i) }?.let { Stair(it.dir) }
                        ?: throw Exception("Initialization:: A tile declared as Stair without Stair info registered")
                    Blocks.VOID -> Void
                }
            }
        }

        val miscLayout = convertJsonToMiscLayout(colors, levels, type, grid.size to grid[0].size)
        squares = zip(blocks, miscLayout, biomes) { block, addi, biom ->
            Square(block, addi.color, addi.level, biom, null, null, null, null, null)
        }

        // This loop is a temporary solution for assigning items over squares
        itemLayout.forEachIndexed { i, line ->
            line.forEachIndexed { j, it ->
                when (it) {
                    Items.NONE -> {
                    }
                    Items.GEM -> squares!![i][j].gem = Gem()
                    Items.CLOSEDSWITCH -> squares!![i][j].switch = Switch(on = false)
                    Items.OPENEDSWITCH -> squares!![i][j].switch = Switch(on = true)
                    Items.BEEPER -> squares!![i][j].beeper = Beeper()
                    Items.PORTAL -> squares!![i][j].portal = portals?.entries?.firstOrNull {
                        it.value == Coordinate(j, i)
                    }?.key
                    Items.PLATFORM -> squares!![i][j].platform = platforms?.entries?.firstOrNull {
                        it.value == Coordinate(j, i)
                    }?.key
                }
            }
        }

        players!!.forEach { squares!![it.value.x][it.value.y].players.add(it.key) }

    }

    private fun preInitChecks() {
        assert (grid.size == itemLayout.size && grid.size == colors.size && grid.size == levels.size
                && grid[0].size == itemLayout[0].size && grid[0].size == colors[0].size && grid[0].size == levels[0].size
        ) { "Initialization:: grid, itemlayout and misclayout must have same dimension!" }

        assert (lockdatas.all { it.coo.let { grid[it.y][it.x] == Blocks.LOCK } }) {
            "Initialization:: A Lock registration corresponding to a tile which is not a Lock"
        }

        assert (platformdatas.all { it.coo.let { itemLayout[it.y][it.x] == Items.PLATFORM } }) {
            "Initialization:: A Platform registration corresponding to a coordinate which has no Platform"
        }

        assert (lockdatas.all { it.controlled.all { itemLayout[it.y][it.x] == Items.PLATFORM } }) {
            "Initialization:: A coordinate controlled by a Lock has no Platform"
        }

        assert (portaldatas.all { it.coo.let { itemLayout[it.y][it.x] == Items.PORTAL } }) {
            "Initialization:: A Portal registration corresponding to a coordinate which has no portal"
        }

        assert (stairdatas.all { it.coo.let { grid[it.y][it.x] == Blocks.STAIR } }) {
            "Initialization:: A Stair registration corresponding to a tile which is not a Stair"
        }

        grid.forEachIndexed { i, line ->
            line.forEachIndexed { j, _ ->
                if (grid[i][j] == Blocks.LOCK) {
                    assert (lockdatas.any { it.coo.x == j && it.coo.y == i }) {
                        "Initialization:: A tile Lock has no corresponding registration in data"
                    }
                }
                if (grid[i][j] == Blocks.STAIR) {
                    assert (stairdatas.any { it.coo.x == j && it.coo.y == i }) {
                        "Initialization:: A tile Stair has no corresponding registration in data"
                    }
                }
                if (itemLayout[i][j] == Items.PORTAL) {
                    assert (portaldatas.any { it.coo.x == j && it.coo.y == i }) {
                        "Initialization:: A coordinate contains Portal but not registered in data"
                    }
                }

                if (itemLayout[i][j] == Items.PLATFORM) {
                    assert (platformdatas.any { it.coo.x == j && it.coo.y == i }) {
                        "Initialization:: A coordinate contains Platform but not registered in data"
                    }
                }
            }
        }
        // In the future we might remove the itemLayout and introduce new arrays for each items
    }

    fun start() {
        val input: CharStream = CharStreams.fromString(code)
        val lexer = amatsukazeGrammarLexer(input)
        val tokens = CommonTokenStream(lexer)
        val parser = amatsukazeGrammarParser(tokens)
        val tree: ParseTree = parser.top_level()
        val playground = Playground(
            squares!!,
            portals!!.toMutableMap(),
            locks!!.toMutableMap(),
            players!!.toMutableMap(),
            calculateInitialGem(itemLayout)) // TODO Fix this with future incoming data
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