package org.ironica.amatsukaze

class ColorfulManager(override val playground: Playground): AbstractManager {

    override var consoleLog: String = ""
    override var special: String = ""

    override val firstId = playground.players.map { it.id }.sorted()[0]

    @PlaygroundFunction(type = PF.Property, ret = PFType.Bool ,self = PFType.Self, arg1 = PFType.None, arg2 = PFType.None)
    override fun isOnGem(id: Int) = getPlayer(id).isOnGem
    @PlaygroundFunction(type = PF.Property, ret = PFType.Bool ,self = PFType.Self, arg1 = PFType.None, arg2 = PFType.None)
    override fun isOnOpenedSwitch(id: Int) = getPlayer(id).isOnOpenedSwitch
    @PlaygroundFunction(type = PF.Property, ret = PFType.Bool ,self = PFType.Self, arg1 = PFType.None, arg2 = PFType.None)
    override fun isOnClosedSwitch(id: Int) = getPlayer(id).isOnClosedSwitch
    @PlaygroundFunction(type = PF.Property, ret = PFType.Bool ,self = PFType.Self, arg1 = PFType.None, arg2 = PFType.None)
    override fun isOnBeeper(id: Int) = getPlayer(id).isOnBeeper
    @PlaygroundFunction(type = PF.Property, ret = PFType.Bool ,self = PFType.Self, arg1 = PFType.None, arg2 = PFType.None)
    override fun isAtHome(id: Int) = getPlayer(id).isAtHome
    @PlaygroundFunction(type = PF.Property, ret = PFType.Bool ,self = PFType.Self, arg1 = PFType.None, arg2 = PFType.None)
    override fun isInDesert(id: Int) = getPlayer(id).isInDesert
    @PlaygroundFunction(type = PF.Property, ret = PFType.Bool ,self = PFType.Self, arg1 = PFType.None, arg2 = PFType.None)
    override fun isInForest(id: Int) = getPlayer(id).isInForest
    @PlaygroundFunction(type = PF.Property, ret = PFType.Bool ,self = PFType.Self, arg1 = PFType.None, arg2 = PFType.None)
    override fun isOnPortal(id: Int) = getPlayer(id).isOnPortal
    @PlaygroundFunction(type = PF.Property, ret = PFType.Bool ,self = PFType.Self, arg1 = PFType.None, arg2 = PFType.None)
    override fun isBlocked(id: Int) = getPlayer(id).isBlocked
    @PlaygroundFunction(type = PF.Property, ret = PFType.Bool ,self = PFType.Self, arg1 = PFType.None, arg2 = PFType.None)
    override fun isBlockedLeft(id: Int) = getPlayer(id).isBlockedLeft
    @PlaygroundFunction(type = PF.Property, ret = PFType.Bool ,self = PFType.Self, arg1 = PFType.None, arg2 = PFType.None)
    override fun isBlockedRight(id: Int) = getPlayer(id).isBlockedRight
    @PlaygroundFunction(type = PF.Property, ret = PFType.Bool ,self = PFType.Self, arg1 = PFType.None, arg2 = PFType.None)
    override fun collectedGem(id: Int) = getPlayer(id).collectedGem

    @PlaygroundFunction(type = PF.Method, ret = PFType.None, self = PFType.Self, arg1 = PFType.None, arg2 = PFType.None)
    override fun turnLeft(id: Int) {
        getPlayer(id).turnLeft()
        printGrid()
        appendEntry()
    }
    @PlaygroundFunction(type = PF.Method, ret = PFType.None, self = PFType.Self, arg1 = PFType.None, arg2 = PFType.None)
    override fun moveForward(id: Int) {
        getPlayer(id).moveForward()
        printGrid()
        appendEntry()
        if (getPlayer(id).isOnPortal()) {
            getPlayer(id).stepIntoPortal()
            printGrid()
            appendEntry()
        }
    }
    @PlaygroundFunction(type = PF.Method, ret = PFType.None, self = PFType.Self, arg1 = PFType.None, arg2 = PFType.None)
    override fun collectGem(id: Int) {
        getPlayer(id).collectGem()
        printGrid()
        this.special = "GEM"
        appendEntry()
    }
    @PlaygroundFunction(type = PF.Method, ret = PFType.None, self = PFType.Self, arg1 = PFType.None, arg2 = PFType.None)
    override fun toggleSwitch(id: Int) {
        getPlayer(id).toggleSwitch()
        printGrid()
        this.special = "SWITCH"
        appendEntry()
    }
    @PlaygroundFunction(type = PF.Method, ret = PFType.None, self = PFType.Self, arg1 = PFType.None, arg2 = PFType.None)
    override fun takeBeeper(id: Int) {
        getPlayer(id).takeBeeper()
        printGrid()
        this.special = "TAKEBEEPER"
        appendEntry()
    }
    @PlaygroundFunction(type = PF.Method, ret = PFType.None, self = PFType.Self, arg1 = PFType.None, arg2 = PFType.None)
    override fun dropBeeper(id: Int) {
        getPlayer(id).dropBeeper()
        printGrid()
        this.special = "DROPBEEPER"
        appendEntry()
    }


    @PlaygroundFunction(type = PF.Method, ret = PFType.None, self = PFType.Self, arg1 = PFType.Color, arg2 = PFType.None)
    fun changeColor(id: Int, c: Color) {
        getPlayer(id).changeColor(c)
        printGrid()
        appendEntry()
    }


    override fun printGrid() {
        playground.layout2s.forEach {
            it.forEach {
                when ((it as ColorfulTile).color) {
                    Color.WHITE -> '白'
                    Color.BLACK -> '黑'
                    Color.SILVER -> '银'
                    Color.GREY -> '灰'
                    Color.RED -> '红'
                    Color.ORANGE -> '橙'
                    Color.GOLD -> '金'
                    Color.PINK -> '粉'
                    Color.YELLOW -> '黄'
                    Color.BEIGE -> '米'
                    Color.BROWN -> '棕'
                    Color.GREEN -> '绿'
                    Color.AZURE -> '碧'
                    Color.CYAN -> '青'
                    Color.ALICEBLUE -> '蓝'
                    Color.PURPLE -> '紫'
                }.apply { print(it) }
                println()
            }
        }
    }

    override fun appendEntry() {
        if (payloadStorage.size > 1000)
            throw Exception("Too many entries!")
        val currentGrid = Array(playground.grid.size) { Array(playground.grid[0].size) { Block.OPEN } }
        for (i in playground.grid.indices)
            for (j in playground.grid[0].indices)
                currentGrid[i][j] = playground.grid[i][j]
        val currentLayout = Array(playground.layout.size) { Array(playground.layout[0].size) { Item.NONE } }
        for (i in playground.layout.indices)
            for (j in playground.layout[0].indices)
                currentLayout[i][j] = playground.layout[i][j]
        val currentMiscLayout = Array(playground.layout2s.size) { Array<Tile>(playground.layout2s[0].size) { ColorfulTile(Color.WHITE) } }
        for (i in playground.layout2s.indices)
            for (j in playground.layout2s[0].indices) {
                currentMiscLayout[i][j] = playground.layout2s[i][j]
            }
        val currentPortals = Array(playground.portals.size) { Portal() }
        for (i in playground.portals.indices)
            currentPortals[i] = playground.portals[i]
        val serializedPlayers = playground.players.map {
            SerializedPlayer(it.id, it.coo.x, it.coo.y, it.dir, if (it is Specialist) Role.SPECIALIST else Role.PLAYER, it.stamina ?: 0) }.toTypedArray()
        val payload = Payload(
            serializedPlayers,
            currentPortals,
            SerializedPlayground(currentGrid, currentLayout, currentMiscLayout),
            this.consoleLog,
            this.special
        )
        payloadStorage.add(payload)
        this.special = ""
    }
}