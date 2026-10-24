package com.tosin.docprocessor.data.parser.common

import org.junit.Assert.assertEquals
import org.junit.Test

class ListNumberingStateTest {

    @Test
    fun `increments per key and level`() {
        val state = ListNumberingState()
        assertEquals(1, state.nextItem("num1", 0))
        assertEquals(2, state.nextItem("num1", 0))
        assertEquals(1, state.nextItem("num2", 0))
    }

    @Test
    fun `deeper levels restart when the parent advances`() {
        val state = ListNumberingState()
        state.nextItem("num1", 0)
        assertEquals(1, state.nextItem("num1", 1))
        assertEquals(2, state.nextItem("num1", 1))
        state.nextItem("num1", 0)
        assertEquals(1, state.nextItem("num1", 1))
    }

    @Test
    fun `sibling levels are independent`() {
        val state = ListNumberingState()
        assertEquals(1, state.nextItem("num1", 0))
        assertEquals(1, state.nextItem("num1", 1))
        assertEquals(2, state.nextItem("num1", 0))
    }

    @Test
    fun `restart resets the level and everything below`() {
        val state = ListNumberingState()
        state.nextItem("num1", 0)
        state.nextItem("num1", 1)
        state.restart("num1", level = 0)
        assertEquals(1, state.nextItem("num1", 0))
        assertEquals(1, state.nextItem("num1", 1))
    }
}
