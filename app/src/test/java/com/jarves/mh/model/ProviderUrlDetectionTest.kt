package com.jarves.mh.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderUrlDetectionTest {
    @Test
    fun detectsKnownProvidersFromPastedUrls() {
        assertEquals(ProviderKind.ANTHROPIC, inferProviderKindForUrl("https://api.anthropic.com"))
        assertEquals(ProviderKind.DEEPSEEK, inferProviderKindForUrl("https://api.deepseek.com/anthropic"))
        assertEquals(ProviderKind.KIMI, inferProviderKindForUrl("https://api.moonshot.ai/anthropic"))
        assertEquals(ProviderKind.LLM_ROUTER, inferProviderKindForUrl("https://openrouter.ai/api"))
        assertEquals(ProviderKind.NVIDIA_NIM, inferProviderKindForUrl("https://integrate.api.nvidia.com/v1"))
        assertEquals(ProviderKind.OPENCODE_ZEN, inferProviderKindForUrl("https://opencode.ai/zen/v1"))
    }

    @Test
    fun detectsHostWithoutSchemeAndWithPort() {
        assertEquals(ProviderKind.DEEPSEEK, inferProviderKindForUrl("api.deepseek.com/anthropic"))
        assertEquals(ProviderKind.ANTHROPIC, inferProviderKindForUrl("https://api.anthropic.com:443"))
    }

    @Test
    fun unknownAndGoogleGatewaysFallBackToCustom() {
        assertEquals(ProviderKind.CUSTOM, inferProviderKindForUrl("https://api.openai.com/v1"))
        assertEquals(ProviderKind.CUSTOM, inferProviderKindForUrl("https://generativelanguage.googleapis.com/v1beta/openai/"))
        assertEquals(ProviderKind.CUSTOM, inferProviderKindForUrl("https://my-gateway.example.com/v1"))
        assertEquals(ProviderKind.CUSTOM, inferProviderKindForUrl(""))
    }

    @Test
    fun infersProtocolForCommonGatewayShapes() {
        assertEquals("openai-completions", inferredDshApiForUrl("https://api.example.com/v1/"))
        assertEquals("anthropic-messages", inferredDshApiForUrl("https://api.example.com/anthropic"))
        assertEquals("openai-responses", inferredDshApiForUrl("https://api.example.com/v1/responses"))
        // Gemini's OpenAI-compatible endpoint must not be treated as Anthropic.
        assertEquals(
            "openai-completions",
            inferredDshApiForUrl("https://generativelanguage.googleapis.com/v1beta/openai/"),
        )
        assertEquals("openai-completions", inferredDshApiForUrl("https://api.openai.com/v1/chat/completions"))
    }

    @Test
    fun detectionIsClampedToProvidersTheAgentSupports() {
        // DeepSeek-Harness cannot use OpenCode Zen, so it falls back to Custom.
        assertEquals(
            ProviderKind.CUSTOM,
            detectProviderForAgent(AgentKind.DEEPSEEK_HARNESS, "https://opencode.ai/zen/v1"),
        )
        // Claude Code supports DeepSeek.
        assertEquals(
            ProviderKind.DEEPSEEK,
            detectProviderForAgent(AgentKind.CLAUDE_CODE, "https://api.deepseek.com/anthropic"),
        )
        // Antigravity uses Google login and exposes no API-key providers.
        assertNull(detectProviderForAgent(AgentKind.ANTIGRAVITY, "https://api.deepseek.com/anthropic"))
    }
}
