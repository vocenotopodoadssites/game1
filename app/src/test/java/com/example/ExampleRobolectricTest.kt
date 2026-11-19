package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.alienfall.model.HeroClass
import com.example.alienfall.renderer.ModelAssetLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ALIENFALL", appName)
    }

    @Test
    fun `berserker glb asset loads with texture and normalized bounds`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = ModelAssetLoader(context)
        val geom = loader.tryLoadGlb(HeroClass.BERSERKER.glbModelPath)
        assertNotNull(geom)
        val loaded = geom!!
        assertTrue(loaded.vertices.size >= 20_000 * 12)
        assertTrue(loaded.indices.size >= 80_000)
        assertNotNull(loaded.baseColorImageBytes)
        assertTrue(loaded.baseColorImageBytes!!.isNotEmpty())
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in loaded.vertices.indices step 12) {
            val y = loaded.vertices[i + 1]
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        assertTrue(minY >= -0.01f && minY <= 0.01f)
        assertTrue(maxY in 2.10f..2.20f)
        // Confirm diagnostic truth: current berserker.glb has no skeleton or vertex skinning weights
        assertEquals(false, loaded.hasSkeleton)
        assertEquals(false, loaded.hasSkinning)
        assertEquals(0, loaded.boneCount)
    }

    @Test
    fun `fbx animation loader loads all imported clips and moves mixamo bones across frames`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = com.example.alienfall.renderer.FbxAnimationLoader(context)
        loader.preloadAll()
        assertTrue("Missing FBX clips: ${loader.emptyOrMissingClips}", loader.emptyOrMissingClips.isEmpty())

        for (clip in com.example.alienfall.renderer.HeroMotionClip.entries) {
            val parsed = loader.getLoadedClip(clip)
            assertNotNull("Failed to parse $clip", parsed)
            val matsStart = parsed!!.evaluateSkeletonWorldMatrices(0.0f)
            val matsMid = parsed.evaluateSkeletonWorldMatrices(0.5f)
            assertTrue("Expected full Mixamo skeleton in $clip", matsStart.size >= 50)

            var maxDelta = 0f
            for (i in matsStart.indices) {
                for (k in 0 until 16) {
                    val d = kotlin.math.abs(matsStart[i][k] - matsMid[i][k])
                    if (d > maxDelta) maxDelta = d
                }
            }
            assertTrue("Bones remained static in $clip (maxDelta=$maxDelta)", maxDelta > 0.01f)
        }

        // Verify skinning matrix retargeting produces distinct bone matrices when a rigged GLB is provided
        val dummySkin = com.example.alienfall.renderer.GlbSkinData(
            jointNames = listOf("mixamorig:Hips", "mixamorig:RightArm", "mixamorig:LeftUpLeg"),
            jointParentIndices = intArrayOf(-1, 0, 0),
            inverseBindMatrices = FloatArray(48),
            vertexJointsAndWeights = FloatArray(8),
            normScale = 1f,
            centerX = 0f,
            minY = 0f,
            centerZ = 0f
        )
        val skinT0 = loader.computeSkinMatrices(dummySkin, com.example.alienfall.renderer.HeroMotionClip.JOGGING, 0.0f, 68)
        val skinT5 = loader.computeSkinMatrices(dummySkin, com.example.alienfall.renderer.HeroMotionClip.JOGGING, 0.5f, 68)
        var skinDiff = 0f
        for (i in skinT0.indices) {
            skinDiff += kotlin.math.abs(skinT0[i] - skinT5[i])
        }
        assertTrue("Skinning matrices did not change across frames", skinDiff > 0.05f)
    }

    @Test
    fun `main activity launches home screen and navigates to mission`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("play_button").assertExists()
        composeRule.onNodeWithTag("nav_characters_button").assertExists()
        composeRule.onNodeWithTag("play_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pause_button").assertExists()
        composeRule.onNodeWithTag("touch_move_surface").assertExists()
        composeRule.onNodeWithTag("movement_joystick").assertDoesNotExist()
    }
}
