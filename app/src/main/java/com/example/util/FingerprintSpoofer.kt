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
        val timezone = profile.spoofedTimezone
        val geo = profile.spoofedGeolocation.split(",")
        val lat = if (geo.size == 2) geo[0].trim() else "0"
        val lon = if (geo.size == 2) geo[1].trim() else "0"

        val isMobile = if (ua.contains("Mobile", ignoreCase = true) || ua.contains("iPhone", ignoreCase = true)) "true" else "false"
        val platformName = when {
            platform.contains("Win", ignoreCase = true) -> "Windows"
            platform.contains("Mac", ignoreCase = true) -> "macOS"
            platform.contains("iPhone", ignoreCase = true) || platform.contains("iOS", ignoreCase = true) -> "iOS"
            else -> "Android"
        }
        val modelName = if (isMobile == "true") {
            if (platformName == "iOS") "iPhone" else "Android Phone"
        } else ""
        val platformVersionName = when (platformName) {
            "Windows" -> "10.0.0"
            "macOS" -> "14.4.1"
            "iOS" -> "17.4"
            else -> "14.0.0"
        }

        // Minified JS to spoof navigator parameters and Canvas/WebGL attributes with undetectability
        return """
            (function() {
                if (window.__incognav_shield_installed__) return;
                window.__incognav_shield_installed__ = true;

                // Stealth wrapper helper to preserve Function.prototype.toString showing '[native code]'
                const stealthWrap = (owner, funcName, wrapperFunc) => {
                    try {
                        const originalFunc = owner[funcName];
                        if (!originalFunc) return;
                        Object.defineProperty(owner, funcName, {
                            value: wrapperFunc,
                            configurable: true,
                            writable: true
                        });
                        Object.defineProperty(wrapperFunc, 'toString', {
                            value: function() {
                                if (this === wrapperFunc) return "function " + funcName + "() { [native code] }";
                                return Function.prototype.toString.call(this);
                            },
                            configurable: true,
                            writable: true
                        });
                    } catch (e) {
                        console.error("Failed stealth wrap on " + funcName, e);
                    }
                };

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

                    if (navigator.userAgentData) {
                        const isMobileVal = $isMobile;
                        const pfVal = "$platformName";
                        const modelVal = "$modelName";
                        const pfVerVal = "$platformVersionName";
                        
                        const spoofedUserAgentData = {
                            brands: [
                                { brand: 'Chromium', version: '124' },
                                { brand: 'Google Chrome', version: '124' },
                                { brand: 'Not-A.Brand', version: '124' }
                            ],
                            mobile: isMobileVal,
                            platform: pfVal
                        };

                        Object.defineProperty(navigator, 'userAgentData', {
                            get: function() { return spoofedUserAgentData; }
                        });

                        stealthWrap(navigator.userAgentData, 'getHighEntropyValues', function(hints) {
                            return new Promise((resolve) => {
                                const response = {
                                    brands: [
                                        { brand: 'Chromium', version: '124' },
                                        { brand: 'Google Chrome', version: '124' },
                                        { brand: 'Not-A.Brand', version: '124' }
                                    ],
                                    mobile: isMobileVal,
                                    platform: pfVal,
                                    architecture: pfVal === "Windows" ? "x86" : (pfVal === "macOS" ? "arm" : ""),
                                    bitness: "64",
                                    model: modelVal,
                                    platformVersion: pfVerVal,
                                    uaFullVersion: "124.0.0.0"
                                };
                                const result = {};
                                if (hints && Array.isArray(hints)) {
                                    hints.forEach(hint => {
                                        if (hint in response) result[hint] = response[hint];
                                    });
                                }
                                resolve(Object.assign({}, response, result));
                            });
                        });
                    }
                } catch (e) {
                    console.error("Navigator spoofing blocked: ", e);
                }

                // Spoof Timezone
                try {
                    const targetTimeZone = "$timezone";
                    const nativeGetTimezoneOffset = Date.prototype.getTimezoneOffset;
                    
                    stealthWrap(Date.prototype, 'getTimezoneOffset', function() {
                        try {
                            const invdate = new Date(this.toLocaleString('en-US', { timeZone: targetTimeZone }));
                            const diff = this.getTime() - invdate.getTime();
                            return Math.round(diff / 60000);
                        } catch(e) {
                            return nativeGetTimezoneOffset.apply(this);
                        }
                    });

                    const originalResolvedOptions = Intl.DateTimeFormat.prototype.resolvedOptions;
                    stealthWrap(Intl.DateTimeFormat.prototype, 'resolvedOptions', function() {
                        const options = originalResolvedOptions.apply(this);
                        options.timeZone = targetTimeZone;
                        return options;
                    });
                } catch(e) { console.error("TimeZone spoofing error: ", e); }

                // Protect ISP and Local IP leaks via fully-stealthy WebRTC wrappers
                try {
                    const origPC = window.RTCPeerConnection || window.webkitRTCPeerConnection || window.mozRTCPeerConnection;
                    if (origPC) {
                        const spoofedPC = function(config, constraints) {
                            const pc = new origPC(config, constraints);
                            
                            let userIceCallback = null;
                            Object.defineProperty(pc, 'onicecandidate', {
                                get: function() { return userIceCallback; },
                                set: function(cb) { userIceCallback = cb; }
                            });
                            
                            pc.addEventListener('icecandidate', function(e) {
                                e.stopImmediatePropagation();
                            }, true);
                            
                            const origLocalDesc = Object.getOwnPropertyDescriptor(origPC.prototype, 'localDescription');
                            if (origLocalDesc) {
                                Object.defineProperty(pc, 'localDescription', {
                                    get: function() {
                                        const desc = origLocalDesc.get.call(this);
                                        if (desc && desc.sdp) {
                                            desc.sdp = desc.sdp.replace(/([0-9]{1,3}\.){3}[0-9]{1,3}/g, "0.0.0.0");
                                        }
                                        return desc;
                                    }
                                });
                            }
                            return pc;
                        };
                        
                        spoofedPC.prototype = origPC.prototype;
                        
                        Object.defineProperty(spoofedPC, 'toString', {
                            value: function() { return "function RTCPeerConnection() { [native code] }"; }
                        });
                        
                        window.RTCPeerConnection = spoofedPC;
                        if (window.webkitRTCPeerConnection) window.webkitRTCPeerConnection = spoofedPC;
                        if (window.mozRTCPeerConnection) window.mozRTCPeerConnection = spoofedPC;
                    }
                } catch(e) { console.error("WebRTC block error:", e); }

                // Spoof Geolocation
                try {
                    const spoofedCoords = {
                        latitude: $lat,
                        longitude: $lon,
                        accuracy: 100,
                        altitude: null,
                        altitudeAccuracy: null,
                        heading: null,
                        speed: null
                    };

                    stealthWrap(navigator.geolocation, 'getCurrentPosition', function(success, error, options) {
                        success({ coords: spoofedCoords, timestamp: Date.now() });
                    });
                    stealthWrap(navigator.geolocation, 'watchPosition', function(success, error, options) {
                        success({ coords: spoofedCoords, timestamp: Date.now() });
                        return 1;
                    });
                } catch(e) { console.error("Geo spoofing:", e); }

                // Spoof Canvas hash signatures
                if ($canvasNoise) {
                    try {
                        const originalToDataURL = HTMLCanvasElement.prototype.toDataURL;
                        stealthWrap(HTMLCanvasElement.prototype, 'toDataURL', function() {
                            const ctx = this.getContext('2d');
                            if (ctx) {
                                const oldStyle = ctx.fillStyle;
                                ctx.fillStyle = "rgba(255,255,255,0.005)";
                                ctx.fillRect(Math.random() * 5, Math.random() * 5, 1, 1);
                                ctx.fillStyle = oldStyle;
                            }
                            return originalToDataURL.apply(this, arguments);
                        });

                        const originalGetImageData = CanvasRenderingContext2D.prototype.getImageData;
                        stealthWrap(CanvasRenderingContext2D.prototype, 'getImageData', function() {
                            const imageData = originalGetImageData.apply(this, arguments);
                            const len = imageData.data.length;
                            if (len > 4) {
                                imageData.data[len - 4] = (imageData.data[len - 4] + 1) % 256;
                            }
                            return imageData;
                        });
                    } catch (e) {
                        console.error("Canvas spoofing failed: ", e);
                    }
                }

                // Spoof WebGL GPU markers
                if ($webglSpoof) {
                    try {
                        const originalGetParameter = WebGLRenderingContext.prototype.getParameter;
                        stealthWrap(WebGLRenderingContext.prototype, 'getParameter', function(parameter) {
                            if (parameter === 37445) return "$webglVendor"; 
                            if (parameter === 37446) return "$webglRenderer";
                            return originalGetParameter.apply(this, arguments);
                        });

                        if (window.WebGL2RenderingContext) {
                            const originalGetParameter2 = WebGL2RenderingContext.prototype.getParameter;
                            stealthWrap(WebGL2RenderingContext.prototype, 'getParameter', function(parameter) {
                                if (parameter === 37445) return "$webglVendor";
                                if (parameter === 37446) return "$webglRenderer";
                                return originalGetParameter2.apply(this, arguments);
                            });
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
