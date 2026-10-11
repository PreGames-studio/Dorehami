package ir.dorehami.games.chess.ui.gl

/** GLSL ES 1.00 sources (OpenGL ES 2.0 runs on every Android 7+ device). */
internal object Shaders {

    /** Lit pieces and board sides: per-pixel Blinn-Phong + hemisphere ambient + rim light. */
    const val PIECE_VS = """
        uniform mat4 uMVP;
        uniform mat4 uModel;
        attribute vec3 aPos;
        attribute vec3 aNormal;
        attribute float aShade;
        varying vec3 vNormal;
        varying vec3 vWorld;
        varying float vShade;
        varying float vLocalY;
        void main() {
            vec4 world = uModel * vec4(aPos, 1.0);
            vWorld = world.xyz;
            vNormal = (uModel * vec4(aNormal, 0.0)).xyz;
            vShade = aShade;
            vLocalY = aPos.y;
            gl_Position = uMVP * vec4(aPos, 1.0);
        }
    """

    const val PIECE_FS = """
        precision mediump float;
        uniform vec3 uColor;
        uniform vec3 uEye;
        uniform vec3 uLight;
        uniform float uSpec;
        uniform float uShininess;
        uniform vec3 uEmissive;
        uniform float uAlpha;
        uniform float uAo;
        varying vec3 vNormal;
        varying vec3 vWorld;
        varying float vShade;
        varying float vLocalY;
        void main() {
            vec3 n = normalize(vNormal);
            vec3 v = normalize(uEye - vWorld);
            float diff = max(dot(n, uLight), 0.0);
            vec3 h = normalize(uLight + v);
            float spec = pow(max(dot(n, h), 0.0), uShininess) * uSpec;
            float hemi = 0.5 + 0.5 * n.y;
            vec3 ambient = mix(vec3(0.17, 0.14, 0.12), vec3(0.40, 0.40, 0.45), hemi);
            float fill = max(dot(n, normalize(vec3(0.65, 0.30, -0.70))), 0.0) * 0.22;
            float rim = pow(1.0 - max(dot(n, v), 0.0), 3.0);
            float ao = mix(1.0 - uAo, 1.0, smoothstep(0.0, 0.14, vLocalY));
            vec3 base = uColor * vShade;
            vec3 col = base * (ambient * ao + diff * vec3(1.0, 0.95, 0.88) * 0.92 + fill)
                + spec * vec3(1.0, 0.97, 0.92) * vShade
                + rim * vec3(0.50, 0.55, 0.65) * (0.15 + 0.25 * hemi)
                + uEmissive;
            gl_FragColor = vec4(col, uAlpha);
        }
    """

    /** Board top: the Canvas-painted wood texture, lit, with lacquer highlight. */
    const val BOARD_VS = """
        uniform mat4 uMVP;
        attribute vec3 aPos;
        varying vec2 vUv;
        varying vec3 vWorld;
        void main() {
            vWorld = aPos;
            vUv = vec2((aPos.x + 4.6) / 9.2, (aPos.z + 4.6) / 9.2);
            gl_Position = uMVP * vec4(aPos, 1.0);
        }
    """

    const val BOARD_FS = """
        precision mediump float;
        uniform sampler2D uTex;
        uniform vec3 uEye;
        uniform vec3 uLight;
        uniform float uInnerAlpha;
        varying vec2 vUv;
        varying vec3 vWorld;
        void main() {
            vec3 tex = texture2D(uTex, vUv).rgb;
            vec3 n = vec3(0.0, 1.0, 0.0);
            vec3 v = normalize(uEye - vWorld);
            vec3 h = normalize(uLight + v);
            float diff = max(dot(n, uLight), 0.0);
            float spec = pow(max(dot(n, h), 0.0), 60.0) * 0.30;
            float fres = pow(1.0 - max(dot(n, v), 0.0), 4.0) * 0.18;
            float centre = 1.0 - smoothstep(3.0, 7.5, length(vWorld.xz));
            vec3 col = tex * (0.42 + 0.62 * diff) * (0.86 + 0.14 * centre) + vec3(spec + fres);
            bool inner = abs(vWorld.x) < 4.0 && abs(vWorld.z) < 4.0;
            gl_FragColor = vec4(col, inner ? uInnerAlpha : 1.0);
        }
    """

    /** Flat quads on the XZ plane: highlights, contact shadows, the table and the board's shadow. */
    const val FLAT_VS = """
        uniform mat4 uMVP;
        uniform vec3 uCenter;
        uniform vec2 uHalf;
        attribute vec2 aPos;
        varying vec2 vUv;
        void main() {
            vUv = aPos;
            gl_Position = uMVP * vec4(uCenter.x + aPos.x * uHalf.x, uCenter.y, uCenter.z + aPos.y * uHalf.y, 1.0);
        }
    """

    const val FLAT_FS = """
        precision mediump float;
        uniform vec4 uColor;
        uniform float uShape;
        varying vec2 vUv;
        void main() {
            float r = length(vUv);
            float a;
            if (uShape < 0.5) {
                vec2 d = abs(vUv) - vec2(0.84);
                float dist = length(max(d, 0.0)) - 0.10;
                a = 1.0 - smoothstep(-0.02, 0.05, dist);
            } else if (uShape < 1.5) {
                a = 1.0 - smoothstep(0.25, 0.31, r);
            } else if (uShape < 2.5) {
                a = smoothstep(0.70, 0.76, r) * (1.0 - smoothstep(0.90, 0.97, r));
            } else if (uShape < 3.5) {
                a = pow(max(0.0, 1.0 - r), 1.4);
            } else if (uShape < 4.5) {
                a = pow(max(0.0, 1.0 - r), 2.2);
            } else if (uShape < 5.5) {
                a = 1.0 - smoothstep(0.05, 1.0, r);
            } else {
                vec2 d = abs(vUv);
                a = (1.0 - smoothstep(0.82, 1.0, d.x)) * (1.0 - smoothstep(0.82, 1.0, d.y));
            }
            gl_FragColor = vec4(uColor.rgb, uColor.a * a);
        }
    """

    /** Planar projected shadows: geometry squashed onto the board by the shadow matrix. */
    const val SHADOW_VS = """
        uniform mat4 uMVP;
        attribute vec3 aPos;
        void main() {
            gl_Position = uMVP * vec4(aPos, 1.0);
        }
    """

    const val SHADOW_FS = """
        precision mediump float;
        uniform vec4 uColor;
        void main() {
            gl_FragColor = uColor;
        }
    """
}
