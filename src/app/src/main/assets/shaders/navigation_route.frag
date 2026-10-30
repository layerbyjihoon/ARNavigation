#version 300 es
precision mediump float;

in vec4 v_Color;
layout(location = 0) out vec4 o_FragColor;

void main() {
  // Route colors stay readable regardless of the room's estimated illumination.
  o_FragColor = v_Color;
}
