package dolphin.android.apps.minesweeper

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.util.SparseArray
import android.util.SparseIntArray
import androidx.compose.Model
import androidx.core.util.forEach
import kotlin.random.Random

@Model
class MineModel(
    val maxRows: Int, val maxCols: Int,
    var state: GameState = GameState.Start,
    var clock: Long = 0L,
    var row: Int = 6,
    var column: Int = 5,
    var mines: Int = 10,
    var markedMines: Int = 0,
    var loading: Boolean = false,
    var funny: Boolean = false /* a funny mode for YA */
) {
    companion object {
        private const val TAG = "MineModel"
        private const val MINED = -99
    }

    val remainingMines: Int
        get() = mines - markedMines

    private val mineMap = SparseIntArray()
    private val blockState = SparseArray<BlockState>()
    fun getBlockState(row: Int, column: Int): BlockState = blockState[toIndex(row, column)]

    private var firstClick: Boolean = false

    enum class GameState {
        Start, Running, Exploded, Cleared, Review, Destroyed
    }

    /**
     * indicate if the game is running.
     */
    val running: Boolean
        get() = state == GameState.Running || state == GameState.Start

    private val mapSize: Int
        get() = this.row * this.column

    enum class BlockState {
        None, Mined, Text, Marked, Hidden
    }

    fun generateMineMap(row: Int = this.row, column: Int = this.column, mines: Int = this.mines) {
        loading = true
        mineMap.clear()

        this.row = row
        this.column = column

        this.mines = if (mines > mapSize) mapSize else mines //ensure mines smaller than map size
        Log.v(TAG, "create ${row}x$column with ${this.mines} mines")

        markedMines = 0 //reset counter
        clock = 0 //reset clock

        putMinesIntoField()
        calculateField()

        state = GameState.Start
        loading = false
        firstClick = true
    }

    private fun putMinesIntoField() {
        repeat(this.mines) {
            mineMap.put(randomNewMine(), MINED)
        }
    }

    private fun randomNewMine(): Int {
        var i = Random.nextInt(0, mapSize)
        while (mineExists(i)) {
            i = Random.nextInt(0, mapSize)
        }
        return i
    }

    private fun calculateField() {
        repeat(mapSize) { index ->
            if (!mineExists(index)) {//check 8-directions
                var count = 0
                if (notFirstRow(index)) {
                    if (notFirstColumn(index) && mineExists(toNorthWestIndex(index))) ++count
                    if (mineExists(toNorthIndex(index))) ++count
                    if (notLastColumn(index) && mineExists(toNorthEastIndex(index))) ++count
                }
                if (notFirstColumn(index) && mineExists(toWestIndex(index))) ++count
                if (notLastColumn(index) && mineExists(toEastIndex(index))) ++count
                if (notLastRow(index)) {
                    if (notFirstColumn(index) && mineExists(toSouthWestIndex(index))) ++count
                    if (mineExists(toSouthIndex(index))) ++count
                    if (notLastColumn(index) && mineExists(toSouthEastIndex(index))) ++count
                }
                mineMap.put(index, count)
            }
            blockState.put(index, BlockState.None)
        }
    }

    private fun toIndex(row: Int, column: Int) = row * this.column + column

    private fun mineExists(index: Int): Boolean = if (index in 0 until mapSize)
        mineMap[index] == MINED else false

    private fun mineExists(row: Int, column: Int) = mineExists(toIndex(row, column))

    fun getMineIndicator(row: Int, column: Int): Int {
        //Log.d(TAG, "==> ${toIndex(row, column)} ${mineMap[toIndex(row, column)]}")
        return mineMap[toIndex(row, column)]
    }

    private fun toRow(index: Int): Int = index / this.column
    private fun toColumn(index: Int): Int = index % this.column
    private fun notFirstRow(index: Int): Boolean = toRow(index) != 0
    private fun notLastRow(index: Int): Boolean = toRow(index) != (this.row - 1)
    private fun notFirstColumn(index: Int): Boolean = toColumn(index) != 0
    private fun notLastColumn(index: Int): Boolean = toColumn(index) != (this.column - 1)

    private fun toNorthWestIndex(index: Int): Int = toWestIndex(toNorthIndex(index))
    private fun toNorthIndex(index: Int): Int = index - this.column
    private fun toNorthEastIndex(index: Int): Int = toEastIndex(toNorthIndex(index))
    private fun toWestIndex(index: Int): Int = index - 1
    private fun toEastIndex(index: Int): Int = index + 1
    private fun toSouthWestIndex(index: Int): Int = toWestIndex(toSouthIndex(index))
    private fun toSouthIndex(index: Int): Int = index + this.column
    private fun toSouthEastIndex(index: Int): Int = toEastIndex(toSouthIndex(index))

    fun changeState(row: Int, column: Int, state: BlockState) {
        //loading = false
        blockState.put(toIndex(row, column), state)
        //loading = true
    }

    private fun isBlockNotOpen(index: Int): Boolean = when (blockState[index]) {
        BlockState.None -> true
        else -> false
    }

    fun markAsMine(row: Int, column: Int): GameState {
        if (state == GameState.Review) return GameState.Review
        changeState(row, column, BlockState.Marked)
        markedMines++
        state = if (verifyMineClear()) GameState.Cleared else GameState.Running
        if (state == GameState.Cleared) Log.v(TAG, "You won!")
        return state
    }

    /**
     * Possible scenario: mark or leave all mines
     */
    private fun verifyMineClear(): Boolean {
        val suspects = ArrayList<Int>()
        blockState.forEach { key, value ->
            if (value == BlockState.None || value == BlockState.Marked) suspects.add(key)
        }
        //Log.d(TAG, "verify mines: ${suspects.size} ${suspects.none { !mineExists(it) }}")
        return suspects.none { !mineExists(it) }
    }

    private fun moveMineToAnotherPlace(oldIndex: Int) {
        mineMap.put(oldIndex, 0) //remove mine
        var newIndex = randomNewMine()
        while (newIndex == oldIndex) newIndex = randomNewMine()
        Log.v(TAG, "move $oldIndex to $newIndex")
        mineMap.put(newIndex, MINED)
    }

    private val clockHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (running) {
                clock++
                tick()
            }
        }

        fun tick() {
            sendEmptyMessageDelayed(0, 1000)
        }
    }

    fun stepOn(row: Int, column: Int): GameState {
        if (state == GameState.Review) return GameState.Review
        state = if (mineExists(row, column)) {
            if (firstClick) {//recalculate mine map because first click cannot be a mine
                Log.w(TAG, "recalculate mine map")
                loading = true
                moveMineToAnotherPlace(toIndex(row, column)) //move to another place
                calculateField() //recalculate map
                loading = false
                return stepOn(row, column) //step on again, won't be a mine
            } else {
                //reveal not marked mines
                blockState.forEach { key, _ ->
                    if (mineExists(key) && blockState[key] != BlockState.Marked) {
                        blockState.put(key, BlockState.Hidden)
                    }
                }
                changeState(row, column, BlockState.Mined)
                Log.v(TAG, "Game over! you lost!!")
                GameState.Exploded
            }
        } else {//not mine, reveal it
            changeState(row, column, BlockState.Text)
            if (mineMap.get(toIndex(row, column)) == 0) {//auto click
                autoClick0(toIndex(row, column))
            }
            if (verifyMineClear()) GameState.Cleared else GameState.Running
        }
        if (firstClick) {
            clockHandler.tick()
            firstClick = false //mark that we have click at least one block
        }
        return state
    }

    private fun stepOn0(index: Int) {
        if (isBlockNotOpen(index)) {
            //stepOn(toRow(index), toColumn(index))
            blockState.put(index, BlockState.Text)
            if (mineMap.get(index) == 0) {//auto click
                autoClick0(index)
            }
        }
    }

    private fun autoClick0(index: Int) {
        if (notFirstRow(index) && notFirstColumn(index)) stepOn0(toNorthWestIndex(index))
        if (notFirstRow(index)) stepOn0(toNorthIndex(index))
        if (notFirstRow(index) && notLastColumn(index)) stepOn0(toNorthEastIndex(index))
        if (notFirstColumn(index)) stepOn0(toWestIndex(index))
        if (notLastColumn(index)) stepOn0(toEastIndex(index))
        if (notLastRow(index) && notFirstColumn(index)) stepOn0(toSouthWestIndex(index))
        if (notLastRow(index)) stepOn0(toSouthIndex(index))
        if (notLastRow(index) && notLastColumn(index)) stepOn0(toSouthEastIndex(index))
    }

    private var funnyCount = 0

    fun onTheWayToFunnyMode(): Boolean {
        if (row == 5 && column == 4 && mines == 5 && ++funnyCount == 10) {
            Log.w(TAG, "enable YA mode!")
            funny = true
        }
        return row == 5 && column == 4 && mines == 5
    }
}