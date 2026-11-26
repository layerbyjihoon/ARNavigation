#version 300 es
precision mediump float;

in vec4 v_Color;
uniform float u_Baseline;
layout(location = 0) out vec4 o_FragColor;

void main() {
  // Route colors stay readable regardless of the room's estimated illumination.
  o_FragColor = mix(v_Color,vec4(0.65,0.65,0.65,1.0),u_Baseline);
}
