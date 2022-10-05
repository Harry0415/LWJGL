#version 430
uniform float offset;
mat4 buildRotateZ(float rad)
{	mat4 zrot = mat4( 	cos(rad), -sin(rad), 0.0, 0.0,
						sin(rad), cos(rad), 0.0, 0.0,
						0.0, 0.0, 1.0, 0.0,
						0.0, 0.0, 0.0, 1.0 );
	return zrot;
}
void main(void)
{
	mat4 rot = buildRotateZ(-0.26);
	vec4 pos;
	if (gl_VertexID == 0) 
		pos = vec4( 0.25 + offset, -0.25, 0.0, 1.0);
  	else if (gl_VertexID == 1) 
  		pos = vec4(-0.25 + offset, -0.25, 0.0, 1.0);
  	else 
  		pos = vec4( 0.25 + offset, 0.25, 0.0, 1.0);
  	gl_Position = rot * pos;
}