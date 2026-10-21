package com.fgxyx.cutgif

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeShowsEntryActions() {
        composeRule.onNodeWithText("视频转 GIF").assertIsDisplayed()
        composeRule.onNodeWithText("GIF 拆分").assertIsDisplayed()
        composeRule.onNodeWithText("如果好用就请支持一下吧").assertIsDisplayed()
    }

    @Test
    fun gifSplitActionIsAvailable() {
        composeRule.onNodeWithText("GIF 拆分").assertIsDisplayed().assertHasClickAction()
    }
}
