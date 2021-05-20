/*
 * Copyright (c) 2020-2021. kokoro-aya. All right reserved.
 * Amatsukaze - A Playground Server implemented with ANTLR or Kotlin DSL
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.ironica.amatsukaze.playground

import org.ironica.amatsukaze.playground.data.*
import org.ironica.amatsukaze.playground.Direction.*
import org.ironica.amatsukaze.playground.characters.Player
import org.ironica.amatsukaze.playground.characters.Specialist
import kotlin.reflect.KFunction

class Playground(val squares: List<List<Square>>,
                 val portals: MutableMap<Portal, Coordinate>,
                 val locks: MutableMap<Lock, Coordinate>,
                 val characters: MutableMap<Player, Coordinate>,

                 val canSwim: Boolean = false,
                 val userCollision: Boolean = true,

                 ) {

    val initialGem: Int
    val initialSwitch: Int
    val initialBeeper: Int

    init {
        characters.keys.forEach { it.playground = this }

        initialGem = squares.flatten().filter { it.gem != null }.size
        initialSwitch = squares.flatten().filter { it.switch != null }.size
        initialBeeper = squares.flatten().filter { it.beeper != null }.size
    }

    var status: GameStatus = GameStatus.PENDING

    val allGemCollected: Int
        get() = characters.keys.sumOf { it.collectedGem }
    val allGemLeft: Int
        get() = squares.flatten().filter { it.gem != null }.size
    val allBeeperCollected: Int
        get() = characters.keys.sumOf { it.beeperInBag }
    val allBeeperLeft: Int
        get() = squares.flatten().filter { it.beeper != null }.size
    val allOpenedSwitch: Int
        get() = squares.flatten().filter { it.switch?.on == true }.size
    val allClosedSwitch: Int
        get() = squares.flatten().filter { it.switch?.on == false }.size

    fun kill(player: Player) {
        player.stamina = Int.MIN_VALUE / 2
    }

    fun win(): Boolean = status == GameStatus.WIN
    fun lose(): Boolean = status == GameStatus.LOST
    fun pending(): Boolean = status == GameStatus.PENDING

    private val Player.getCoo: Coordinate
        get() = characters[this] ?: throw Exception("Playground:: Find no coordinate for specified character")

    private val Coordinate.asSquare: Square
        get() {
            assert(y in squares.indices && x in squares[0].indices) {
                "Playground:: attempt to convert a coordinate to a square but it's out of bounds"
            }
            return squares[y][x]
        }

    private fun prePrintTile(c: Coordinate): String {
        val sq = c.asSquare
        if (sq.players.isNotEmpty()) return when (sq.players[0].dir) {
            UP -> "上"
            DOWN -> "下"
            LEFT -> "左"
            RIGHT -> "右"
        }
        if (sq.gem != null) return "钻"
        if (sq.switch != null) return if (sq.switch!!.on) "开" else "关"
        if (sq.beeper != null) return "机"
        if (sq.portal != null) return "门"
        if (sq.platform != null) return "台"
        return when (sq.block) {
            Hill -> "山"
            is Lock -> "锁"
            Void -> "无"
            Open -> "空"
            is Home -> "屋"
            is Stair -> "阶"
            Stone -> "石"
            Tree -> "树"
            Water -> "水"
            Desert -> "漠"
            Mountain -> "岳"
        }
    }

    fun printGrid() {
        squares.forEachIndexed { i, row -> row.forEachIndexed { j, _ ->
                print(prePrintTile(Coordinate(j, i)))
            }
            println()
        }
        println()
    }

    private fun incrementATurn() {

    }

    // ---- Begin of Move Helper Functions ---- //

    private fun isTileAccessible(tile: Block): Boolean {
        return when (tile) {
            is Lock, Mountain, Stone, Void -> false
            Water -> canSwim
            else -> true
        }
    }

    private fun isAdjacent(from: Coordinate, to: Coordinate): Boolean {
        return from.x == to.x && from.y == to.y - 1
                || from.x == to.x && from.y == to.y + 1
                || from.y == to.y && from.x == to.x - 1
                || from.y == to.y && from.x == to.x + 1
    }

    // Test if two adjacent blocks are connected by a stair
    /* The direction of stair is the direction of lower edge of this block
     * Will also check the difference of height
     */
    private fun hasStairToward(from: Coordinate, to: Coordinate): Boolean {
        assert (isAdjacent(from, to)) { "Playground:: Must move from a square to an adjacent square" }
        to.asSquare.block.let {
            if (it !is Stair) return false
            if (from.y + 1 == to.y) return it.dir == UP
            if (from.y - 1 == to.y) return it.dir == DOWN
            if (from.x - 1 == to.x) return it.dir == RIGHT
            if (from.x + 1 == to.x) return it.dir == LEFT
            return false
        }
    }

    private fun isMoveBlocked(from: Coordinate, to: Coordinate): Boolean {
        assert(isAdjacent(from, to)) { "Playground:: Must move from a square to an adjacent square [1]" }
        val f = from.asSquare; val t = to.asSquare
        if (!isTileAccessible(t.block)) return true
        if (userCollision && t.players.isNotEmpty()) return true
        return (t.level - f.level).let {
            it < -1 || it > 1 || (it == 1 && !hasStairToward(from, to))
                    || (it == -1 && !hasStairToward(to, from))
        }
    }

    private fun isMoveFromPlatformToPlatformBlocked(from: Coordinate, to: Coordinate): Boolean {
        assert(isAdjacent(from, to)) { "Playground:: Must move from a square to an adjacent square [2]" }
        val f = from.asSquare.platform; val t = to.asSquare.platform
        if (!(f != null && t != null)) return true
        if (userCollision && t.players.isNotEmpty()) return true
        return (t.level != f.level)
    }

    private fun isMoveFromPlatformBlocked(from: Coordinate, to: Coordinate): Boolean {
        assert(isAdjacent(from, to)) { "Playground:: Must move from a square to an adjacent square [3]" }
        val f = from.asSquare.platform; val t = to.asSquare
        if (f == null) return true
        if (userCollision && t.players.isNotEmpty()) return true
        return (t.level != f.level)
    }

    private fun isMoveToPlatformBlocked(from: Coordinate, to: Coordinate): Boolean {
        assert(isAdjacent(from, to)) { "Playground:: Must move from a square to an adjacent square [4]" }
        val f = from.asSquare; val t = to.asSquare.platform
        if (t == null) return true
        if (userCollision && t.players.isNotEmpty()) return true
        return (t.level != f.level)
    }

    private fun findAPath(from: Coordinate, to: Coordinate): Pair<PlayerReceiver?, PlayerReceiver?> {
        assert(isAdjacent(from, to)) { "Playground:: Must move from a square to an adjacent square [A]" }
        if (!isMoveBlocked(from, to)) return PlayerReceiver.TILE to PlayerReceiver.TILE
        if (!isMoveFromPlatformToPlatformBlocked(from, to)) return PlayerReceiver.PLATFORM to PlayerReceiver.PLATFORM
        if (!isMoveFromPlatformBlocked(from, to)) return PlayerReceiver.PLATFORM to PlayerReceiver.TILE
        if (!isMoveToPlatformBlocked(from, to)) return PlayerReceiver.TILE to PlayerReceiver.PLATFORM
        return null to null
    }

    private val Coordinate.up: Coordinate
        get() = Coordinate(this.x, this.y - 1)
    private fun<T> Coordinate.up(func: KFunction<T>, vararg others: Any): T = func.call(this, this.up, others)

    private val Coordinate.down: Coordinate
        get() = Coordinate(this.x, this.y + 1)
    private fun<T> Coordinate.down(func: KFunction<T>, vararg others: Any): T = func.call(this, this.down, others)

    private val Coordinate.left: Coordinate
        get() = Coordinate(this.x - 1, this.y)
    private fun<T> Coordinate.left(func: KFunction<T>, vararg others: Any): T = func.call(this, this.left, others)

    private val Coordinate.right: Coordinate
        get() = Coordinate(this.x + 1, this.y)
    private fun<T> Coordinate.right(func: KFunction<T>, vararg others: Any): T = func.call(this, this.right, others)


    private fun findPathTowardYPlus(char: Player): Pair<PlayerReceiver?, PlayerReceiver?> = char.getCoo.up(::findAPath)
    private fun findPathTowardYMinus(char: Player): Pair<PlayerReceiver?, PlayerReceiver?> = char.getCoo.down(::findAPath)
    private fun findPathTowardXMinus(char: Player): Pair<PlayerReceiver?, PlayerReceiver?> = char.getCoo.left(::findAPath)
    private fun findPathTowardXPlus(char: Player): Pair<PlayerReceiver?, PlayerReceiver?> = char.getCoo.right(::findAPath)

    private fun move(from: Coordinate, to: Coordinate, char: Player, path: Pair<PlayerReceiver?, PlayerReceiver?>) {
        when (path.first!!) {
            PlayerReceiver.TILE -> when (path.second!!) {
                PlayerReceiver.TILE -> {
                    from.asSquare.players.remove(char); to.asSquare.players.add(char)
                }
                PlayerReceiver.PLATFORM -> {
                    from.asSquare.players.remove(char); to.asSquare.platform?.players?.add(char)
                }
            }
            PlayerReceiver.PLATFORM -> when (path.second!!) {
                PlayerReceiver.TILE -> {
                    from.asSquare.platform?.players?.remove(char); to.asSquare.players.add(char)
                }
                PlayerReceiver.PLATFORM -> {
                    from.asSquare.platform?.players?.remove(char); to.asSquare.platform?.players?.remove(char)
                }
            }
        }
    }

    // Need to update reference of this player in the characters list, also entry point for movement
    private fun movePlayerUp(char: Player, path: Pair<PlayerReceiver?, PlayerReceiver?>) = char.getCoo.up(::move, char, path, {
        characters[char] = characters[char]!!.up
    })
    private fun movePlayerDown(char: Player, path: Pair<PlayerReceiver?, PlayerReceiver?>) = char.getCoo.down(::move, char, path, {
        characters[char] = characters[char]!!.down
    })
    private fun movePlayerLeft(char: Player, path: Pair<PlayerReceiver?, PlayerReceiver?>) = char.getCoo.left(::move, char, path, {
        characters[char] = characters[char]!!.left
    })
    private fun movePlayerRight(char: Player, path: Pair<PlayerReceiver?, PlayerReceiver?>) = char.getCoo.right(::move, char, path, {
        characters[char] = characters[char]!!.right
    })

    private fun getPathForward(char: Player): Pair<PlayerReceiver?, PlayerReceiver?> {
        return when (char.dir) {
            UP -> findPathTowardYPlus(char)
            DOWN -> findPathTowardYMinus(char)
            LEFT -> findPathTowardXMinus(char)
            RIGHT -> findPathTowardXPlus(char)
        }
    }

    // ---- End of Move Helper Functions ---- //

    // ---- Start of Player Properties ---- //

    fun playerIsOnGem(char: Player): Boolean {
        return char.isAlive && char.getCoo.asSquare.gem != null
    }

    fun playerIsOnOpenedSwitch(char: Player): Boolean {
        return char.isAlive && char.getCoo.asSquare.switch?.on == true
    }

    fun playerIsOnClosedSwitch(char: Player): Boolean {
        return char.isAlive && char.getCoo.asSquare.switch?.on == false
    }

    fun playerIsOnBeeper(char: Player): Boolean {
        return char.isAlive && char.getCoo.asSquare.beeper != null
    }

    fun playerIsAtHome(char: Player): Boolean {
        return char.isAlive && char.getCoo.asSquare.block is Home
    }

    fun playerIsOnHill(char: Player): Boolean {
        return char.isAlive && char.getCoo.asSquare.block == Hill
    }

    fun playerIsInDesert(char: Player): Boolean {
        return char.isAlive && char.getCoo.asSquare.block == Desert
    }

    fun playerIsInForest(char: Player): Boolean {
        return char.isAlive && char.getCoo.asSquare.block == Tree
    }

    fun playerIsOnPortal(char: Player): Boolean {
        return char.isAlive && char.getCoo.asSquare.portal != null && char.getCoo.asSquare.players.contains(char)
    }

    fun playerIsOnPlatform(char: Player): Boolean {
        return char.isAlive && char.getCoo.asSquare.platform?.players?.contains(char) ?: false
    }

    fun playerIsBlocked(char: Player): Boolean {
        return when (char.dir) {
            UP -> findPathTowardYPlus(char) == null to null
            DOWN -> findPathTowardYMinus(char) == null to null
            LEFT -> findPathTowardXMinus(char) == null to null
            RIGHT -> findPathTowardXPlus(char) == null to null
        }
    }

    fun playerIsBlockedLeft(char: Player): Boolean {
        return when (char.dir) {
            RIGHT -> findPathTowardYPlus(char) == null to null
            LEFT -> findPathTowardYMinus(char) == null to null
            UP -> findPathTowardXMinus(char) == null to null
            DOWN -> findPathTowardXPlus(char) == null to null
        }
    }

    fun playerIsBlockedRight(char: Player): Boolean {
        return when (char.dir) {
            LEFT -> findPathTowardYPlus(char) == null to null
            RIGHT -> findPathTowardYMinus(char) == null to null
            DOWN -> findPathTowardXMinus(char) == null to null
            UP -> findPathTowardXPlus(char) == null to null
        }
    }

    // ---- End of Player Properties ---- //

    // ---- Start of Player Methods ---- //

    fun playerTurnLeft(char: Player): Boolean {
        if (char.isDead) return false
        char.dir = when (char.dir) {
            UP -> LEFT
            LEFT -> DOWN
            DOWN -> RIGHT
            RIGHT -> UP
        }
        // TODO insert stamina rule here
        incrementATurn()
        return true
    }

    fun playerTurnRight(char: Player): Boolean {
        if (char.isDead) return false
        char.dir = when (char.dir) {
            UP -> RIGHT
            RIGHT -> DOWN
            DOWN -> LEFT
            LEFT -> UP
        }
        // TODO insert stamina rule here
        incrementATurn()
        return true
    }

    fun playerMoveForward(char: Player): Boolean {
        if (char.isDead) return false
        val oldTile = char.getCoo.asSquare
        val path = getPathForward(char)
        if (path != null to null) {
            when (char.dir) {
                UP -> movePlayerUp(char, path)
                DOWN -> movePlayerDown(char, path)
                LEFT -> movePlayerLeft(char, path)
                RIGHT -> movePlayerRight(char, path)
            }
            val newTile = char.getCoo.asSquare
            // TODO insert after-move rules here
            // TODO insert stamina rule here
            char.hasJustSteppedIntoPortal = false
            incrementATurn()
            return true
        }
        incrementATurn()
        return false
    }

    fun playerStepIntoPortal(char: Player): Boolean {
        if (char.isDead) return false
        if (playerIsOnPortal(char) && !char.hasJustSteppedIntoPortal) {
            val p = portals.entries.first { it.value == char.getCoo }.key
            if (p.isActive) {
                p.coo.asSquare.players.remove(char)
                p.dest.asSquare.players.add(char)
                characters[char] = p.dest
                // TODO insert stamina rule here
                p.energy -= 1 // TODO insert portal energy change here
                if (p.energy <= 0) p.isActive = false
                char.hasJustSteppedIntoPortal = true
                return true
            }
        }
        return false
    }

    fun playerCollectGem(char: Player): Boolean {
        if (char.isDead) return false
        return if (playerIsOnGem(char)) {
            char.collectedGem += 1
            char.getCoo.asSquare.gem = null
            // TODO add additional gems rule here
            // TODO insert stamina rule here
            incrementATurn()
            true
        } else {
            incrementATurn()
            true
        }
    }

    fun playerToggleSwitch(char: Player): Boolean {
        if (char.isDead) return false
        return when {
            playerIsOnOpenedSwitch(char) -> {
                characters[char]?.asSquare?.switch?.on = false
                // TODO insert stamina rule here
                incrementATurn()
                true
            }
            playerIsOnClosedSwitch(char) -> {
                characters[char]?.asSquare?.switch?.on = true
                // TODO insert stamina rule here
                incrementATurn()
                true
            }
            else -> {
                incrementATurn()
                false
            }
        }
    }

    fun playerTakeBeeper(char: Player): Boolean {
        if (char.isDead) return false
        return if (playerIsOnBeeper(char)) {
            char.beeperInBag += 1
            characters[char]?.asSquare?.beeper = null
            // TODO insert stamina rule here
            incrementATurn()
            true
        } else {
            incrementATurn()
            false
        }
    }

    fun playerDropBeeper(char: Player): Boolean {
        if (char.isDead || char.beeperInBag <= 0) return false
        with (characters[char]!!.asSquare) {
            return if (this.beeper == null) {
                char.beeperInBag -= 1
                this.beeper = Beeper()
                // TODO insert stamina rule here
                incrementATurn()
                true
            } else {
                incrementATurn()
                false
            }
        }
    }

    private fun killACharacter(char: Player) {
        with (characters[char]!!.asSquare) {
            this.players.remove(char)
            this.platform?.players?.remove(char)
            char.stamina = Int.MIN_VALUE / 2
            // We won't remove the character from the list
        }
    }

    fun playerKill(char: Player): Boolean {
        if (char.isDead) return false
        killACharacter(char)
        incrementATurn()
        return true
    }

    fun playerChangeColor(char: Player, color: Color): Boolean {
        if (char.isDead) return false
        char.getCoo.asSquare.color = color
        // TODO insert stamina rule here
        incrementATurn()
        return true
    }

    // ---- End of Player Methods ---- //

    // ---- Start of Specialist Properties & Methods ---- //

    fun specialistIsBeforeLock(char: Specialist): Boolean {
        if (char.isDead) return false
        val coo = char.getCoo
        val (x, y) = coo.asPairXY
        return when (char.dir) {
            UP -> y >= 1 && coo.up.asSquare.block is Lock
            DOWN -> y <= squares.size - 2 && coo.down.asSquare.block is Lock
            LEFT -> x >= 1 && coo.left.asSquare.block is Lock
            RIGHT -> x <= squares[0].size - 2 && coo.right.asSquare.block is Lock
        }
    }

    private fun turnLock(coo: Coordinate, up: Boolean) {
        with (locks.entries.firstOrNull { it.value == coo }?.key
            ?: throw Exception("Playground:: No lock of the coordinate found")) {
            this.controlled.forEach {
                with (it.asSquare) {
                    this.platform?.let {
                        if (up && it.level < 15) it.level += 1
                        else if (it.level > this.level + 1) it.level -= 1
                    } ?: throw Exception("Playground:: Attempt to change level of a platform that doesn't exist")
                }
            }
        }
    }

    private fun lockPos(char: Specialist): Coordinate {
        assert (specialistIsBeforeLock(char))
        return when (char.dir) {
            UP -> char.getCoo.up
            DOWN -> char.getCoo.down
            LEFT -> char.getCoo.left
            RIGHT -> char.getCoo.right
        }
    }

    fun specialistTurnLockUp(char: Specialist): Boolean {
        if (char.isDead) return false
        if (specialistIsBeforeLock(char)) {
            turnLock(lockPos(char), up = true)
            // TODO insert stamina rule here
            incrementATurn()
            return true
        }
        incrementATurn()
        return false
    }

    fun specialistTurnLockDown(char: Specialist): Boolean {
        if (char.isDead) return true
        if (specialistIsBeforeLock(char)) {
            turnLock(lockPos(char), up = false)
            // TODO insert stamina rule here
            incrementATurn()
            return true
        }
        incrementATurn()
        return false
    }

}

