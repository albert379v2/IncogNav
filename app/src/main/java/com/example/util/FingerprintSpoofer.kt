package com.example.util

import com.example.data.BrowserProfile

object FingerprintSpoofer {

    fun getSpoofingScript(profile: BrowserProfile): String {
        val ua = getProfileUserAgent(profile)
        val platform = getProfilePlatform(profile)
        val languages = getProfileLanguages(profile)
        val webgl = getProfileWebGL(profile)
        val webglVendor = webgl.first
        val webglRenderer = webgl.second

        val canvasNoise = if (profile.canvasNoiseEnabled) "true" else "false"
        val webglSpoof = if (profile.webGlSpoofEnabled) "true" else "false"

        // Minified JS to spoof navigator parameters and Canvas/WebGL attributes
        return """
            (function() {
                if (window.__incognav_shield_installed__) return;
                window.__incognav_shield_installed__ = true;

                // Spoof navigator properties
                try {
                    Object.defineProperty(navigator, 'userAgent', {
                        get: function() { return "$ua"; }
                    });
                    Object.defineProperty(navigator, 'platform', {
                        get: function() { return "$platform"; }
                    });
                    Object.defineProperty(navigator, 'languages', {
                        get: function() { return [$languages]; }
                    });
                } catch (e) {
                    console.error("Navigator spoofing blocked: ", e);
                }

                // Spoof Canvas hash signatures
                if ($canvasNoise) {
                    try {
                        const originalToDataURL = HTMLCanvasElement.prototype.toDataURL;
                        HTMLCanvasElement.prototype.toDataURL = function() {
                            const ctx = this.getContext('2d');
                            if (ctx) {
                                // Microscopic invisible noise injection
                                const oldStyle = ctx.fillStyle;
                                ctx.fillStyle = "rgba(255,255,255,0.005)";
                                ctx.fillRect(Math.random() * 5, Math.random() * 5, 1, 1);
                                ctx.fillStyle = oldStyle;
                            }
                            return originalToDataURL.apply(this, arguments);
                        };

                        const originalGetImageData = CanvasRenderingContext2D.prototype.getImageData;
                        CanvasRenderingContext2D.prototype.getImageData = function(sx, sy, sw, sh) {
                            const imageData = originalGetImageData.apply(this, arguments);
                            const len = imageData.data.length;
                            if (len > 4) {
                                // Slightly slide a pixel color channel secretly
                                imageData.data[len - 4] = (imageData.data[len - 4] + 1) % 256;
                            }
                            return imageData;
                        };
                    } catch (e) {
                        console.error("Canvas spoofing failed: ", e);
                    }
                }

                // Spoof WebGL GPU markers
                if ($webglSpoof) {
                    try {
                        const originalGetParameter = WebGLRenderingContext.prototype.getParameter;
                        WebGLRenderingContext.prototype.getParameter = function(parameter) {
                            // UNMASKED_VENDOR_WEBGL_EXT (0x9245) or UNMASKED_RENDERER_WEBGL_EXT (0x9246)
                            if (parameter === 37445) return "$webglVendor"; 
                            if (parameter === 37446) return "$webglRenderer";
                            return originalGetParameter.apply(this, arguments);
                        };

                        if (window.WebGL2RenderingContext) {
                            const originalGetParameter2 = WebGL2RenderingContext.prototype.getParameter;
                            WebGL2RenderingContext.prototype.getParameter = function(parameter) {
                                if (parameter === 37445) return "$webglVendor";
                                if (parameter === 37446) return "$webglRenderer";
                                return originalGetParameter2.apply(this, arguments);
                            };
                        }
                    } catch (e) {
                        console.error("WebGL spoofing failed: ", e);
                    }
                }
            })();
        """.trimIndent()
    }

    fun getProfileUserAgent(profile: BrowserProfile): String {
        return when (profile.userAgentType) {
            "Chrome Desktop" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            "Safari Mac" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15"
            "Chrome Mobile" -> "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            "Safari iPhone" -> "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"
            "Custom" -> if (profile.customUserAgent.isNotEmpty()) profile.customUserAgent else "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            else -> "Mozilla/5.0 (Linux; Android 14; Build/KTU84P) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }
    }

    fun getProfilePlatform(profile: BrowserProfile): String {
        if (profile.spoofedPlatform != "Default") {
            return profile.spoofedPlatform
        }
        return when (profile.userAgentType) {
            "Chrome Desktop" -> "Win32"
            "Safari Mac" -> "MacIntel"
            "Safari iPhone" -> "iPhone"
            "Chrome Mobile" -> "Linux armv8l"
            else -> "Linux armv8l"
        }
    }

    fun getProfileLanguages(profile: BrowserProfile): String {
        if (profile.spoofedLanguages != "Default" && profile.spoofedLanguages.isNotEmpty()) {
            return profile.spoofedLanguages.split(",").joinToString(",") { "'${it.trim()}'" }
        }
        return "'es-MX', 'es', 'en-US', 'en'"
    }

    private fun getProfileWebGL(profile: BrowserProfile): Pair<String, String> {
        return when (profile.userAgentType) {
            "Chrome Desktop" -> Pair("Google Inc. (NVIDIA)", "ANGLE (NVIDIA, NVIDIA GeForce RTX 3060 Direct3D11 vs_5_0 ps_5_0, D3D11)")
            "Safari Mac", "Safari iPhone" -> Pair("Apple Inc.", "Apple GPU")
            "Chrome Mobile" -> Pair("Google Inc. (Qualcomm)", "Adreno (TM) 730")
            else -> Pair("Google Inc. (ARM)", "Mali-G78")
        }
    }
}
