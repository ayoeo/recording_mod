#version 130

uniform sampler2D combined;
uniform sampler2D new;
uniform int blendFactor;
uniform float blendWeight;

uniform vec2 screenSize;
out vec4 fragColor;

void main() {
  vec2 fragCoord = gl_FragCoord.xy;
  vec2 texCoord = fragCoord / screenSize;
  vec4 combined_col = texture(combined, texCoord);
  vec4 new_col = texture(new, texCoord);

  if (blendFactor == 0) {
    fragColor = vec4(new_col.rgb, 1);
  } else {
    // Blend successive frames
    fragColor = vec4(mix(combined_col.rgb, new_col.rgb, blendWeight), 1f);
  }
}
