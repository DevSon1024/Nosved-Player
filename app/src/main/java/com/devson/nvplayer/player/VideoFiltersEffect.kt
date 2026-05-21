package com.devson.nvplayer.player

import android.content.Context
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.devson.nvplayer.repository.PlaybackSettings

@OptIn(UnstableApi::class)
class VideoFiltersEffect(
    private var settings: PlaybackSettings
) : GlEffect {

    fun updateSettings(newSettings: PlaybackSettings) {
        settings = newSettings
    }

    override fun toGlShaderProgram(
        context: Context,
        useHdr: Boolean,
    ): GlShaderProgram = VideoFiltersShaderProgram(
        useHdr = useHdr,
        effect = this,
    )

    override fun isNoOp(
        inputWidth: Int,
        inputHeight: Int,
    ): Boolean = false

    private class VideoFiltersShaderProgram(
        useHdr: Boolean,
        private val effect: VideoFiltersEffect,
    ) : BaseGlShaderProgram(useHdr, 1) {

        private val glProgram = createGlProgram()

        init {
            val identityMatrix = GlUtil.create4x4IdentityMatrix()
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                POSITION_COMPONENT_COUNT,
            )
            glProgram.setFloatsUniform("uTransformationMatrix", identityMatrix)
            glProgram.setFloatsUniform("uTexTransformationMatrix", identityMatrix)
        }

        override fun configure(
            inputWidth: Int,
            inputHeight: Int,
        ): Size {
            glProgram.setFloatsUniform(
                "uTexelSize",
                floatArrayOf(
                    1f / inputWidth,
                    1f / inputHeight,
                ),
            )
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(
            inputTexId: Int,
            presentationTimeUs: Long,
        ) {
            try {
                glProgram.use()
                setFilterUniforms()
                glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                glProgram.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT)
                GlUtil.checkGlError()
            } catch (exception: GlUtil.GlException) {
                throw VideoFrameProcessingException(exception, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            try {
                glProgram.delete()
            } catch (exception: GlUtil.GlException) {
                throw VideoFrameProcessingException(exception)
            }
        }

        private fun setFilterUniforms() {
            val s = effect.settings
            if (!s.shouldApplyVideoFilters) {
                glProgram.setFloatUniform("uBrightness", 0f)
                glProgram.setFloatUniform("uContrast", 0f)
                glProgram.setFloatUniform("uSaturation", 0f)
                glProgram.setFloatUniform("uHue", 0f)
                glProgram.setFloatUniform("uGamma", 1f)
                glProgram.setFloatUniform("uSharpness", 0f)
                return
            }

            glProgram.setFloatUniform("uBrightness", if (s.isVideoBrightnessFilterEnabled) s.videoBrightness else 0f)
            glProgram.setFloatUniform("uContrast", if (s.isVideoContrastFilterEnabled) s.videoContrast else 0f)
            glProgram.setFloatUniform("uSaturation", if (s.isVideoSaturationFilterEnabled) s.videoSaturation else 0f)
            glProgram.setFloatUniform("uHue", if (s.isVideoHueFilterEnabled) s.videoHue else 0f)
            glProgram.setFloatUniform("uGamma", if (s.isVideoGammaFilterEnabled) s.videoGamma else 1f)
            
            val sharpening = if (s.isVideoSharpeningFilterEnabled) s.videoSharpening else 0f
            val sharpness = kotlin.math.sqrt(sharpening.coerceAtLeast(0f)) * SHARPNESS_SCALE
            glProgram.setFloatUniform("uSharpness", sharpness)
        }

        private fun createGlProgram(): GlProgram = try {
            GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        } catch (exception: GlUtil.GlException) {
            throw VideoFrameProcessingException(exception)
        }
    }

    private companion object {
        private const val POSITION_COMPONENT_COUNT = 4
        private const val VERTEX_COUNT = 4
        private const val SHARPNESS_SCALE = 1.0f

        private const val VERTEX_SHADER = """
            #version 100
            attribute vec4 aFramePosition;
            uniform mat4 uTransformationMatrix;
            uniform mat4 uTexTransformationMatrix;
            varying vec2 vTexSamplingCoord;

            void main() {
              gl_Position = uTransformationMatrix * aFramePosition;
              vec4 texturePosition = vec4(aFramePosition.x * 0.5 + 0.5,
                                          aFramePosition.y * 0.5 + 0.5, 0.0, 1.0);
              vTexSamplingCoord = (uTexTransformationMatrix * texturePosition).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #version 100
            precision highp float;
            uniform sampler2D uTexSampler;
            uniform vec2 uTexelSize;
            uniform float uBrightness;
            uniform float uContrast;
            uniform float uSaturation;
            uniform float uHue;
            uniform float uGamma;
            uniform float uSharpness;
            varying vec2 vTexSamplingCoord;

            vec3 rotateHue(vec3 color, float hue) {
              float angle = hue * 0.01745329252;
              float sine = sin(angle);
              float cosine = cos(angle);
              vec3 yiq = vec3(
                dot(color, vec3(0.299, 0.587, 0.114)),
                dot(color, vec3(0.596, -0.274, -0.322)),
                dot(color, vec3(0.211, -0.523, 0.312))
              );
              vec2 chroma = vec2(
                yiq.y * cosine - yiq.z * sine,
                yiq.y * sine + yiq.z * cosine
              );
              yiq = vec3(yiq.x, chroma.x, chroma.y);
              return vec3(
                dot(yiq, vec3(1.0, 0.956, 0.621)),
                dot(yiq, vec3(1.0, -0.272, -0.647)),
                dot(yiq, vec3(1.0, -1.106, 1.703))
              );
            }

            vec3 applyColorFilters(vec3 color) {
              float luma;

              if (uBrightness != 0.0) {
                color = clamp(color + vec3(uBrightness), 0.0, 1.0);
              }
              if (uContrast != 0.0) {
                color = clamp((color - vec3(0.5)) * (1.0 + uContrast) + vec3(0.5), 0.0, 1.0);
              }
              if (uHue != 0.0) {
                color = clamp(rotateHue(color, uHue), 0.0, 1.0);
              }
              if (uSaturation != 0.0) {
                luma = dot(color, vec3(0.299, 0.587, 0.114));
                color = clamp(mix(vec3(luma), color, 1.0 + uSaturation / 100.0), 0.0, 1.0);
              }
              if (uGamma != 1.0) {
                color = clamp(pow(color, vec3(1.0 / max(uGamma, 0.1))), 0.0, 1.0);
              }
              return color;
            }

            void main() {
              vec4 center = texture2D(uTexSampler, vTexSamplingCoord);
              vec3 sourceColor = center.rgb;

              if (uSharpness > 0.0) {
                vec3 north = texture2D(uTexSampler, vTexSamplingCoord + vec2(0.0, -uTexelSize.y)).rgb;
                vec3 south = texture2D(uTexSampler, vTexSamplingCoord + vec2(0.0, uTexelSize.y)).rgb;
                vec3 west = texture2D(uTexSampler, vTexSamplingCoord + vec2(-uTexelSize.x, 0.0)).rgb;
                vec3 east = texture2D(uTexSampler, vTexSamplingCoord + vec2(uTexelSize.x, 0.0)).rgb;
                vec3 northwest = texture2D(uTexSampler, vTexSamplingCoord + vec2(-uTexelSize.x, -uTexelSize.y)).rgb;
                vec3 northeast = texture2D(uTexSampler, vTexSamplingCoord + vec2(uTexelSize.x, -uTexelSize.y)).rgb;
                vec3 southwest = texture2D(uTexSampler, vTexSamplingCoord + vec2(-uTexelSize.x, uTexelSize.y)).rgb;
                vec3 southeast = texture2D(uTexSampler, vTexSamplingCoord + vec2(uTexelSize.x, uTexelSize.y)).rgb;
                vec3 blur = center.rgb * 0.25 + (north + south + west + east) * 0.125 + (northwest + northeast + southwest + southeast) * 0.0625;
                sourceColor = clamp(center.rgb + (center.rgb - blur) * uSharpness, 0.0, 1.0);
              }

              gl_FragColor = vec4(applyColorFilters(sourceColor), center.a);
            }
        """
    }
}
