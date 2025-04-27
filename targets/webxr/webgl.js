var hasXRSession = false;

const canvas = document.getElementById('glcanvas');
const gl = canvas.getContext('webgl', { xrCompatible: true });
canvas.width = window.innerWidth;
canvas.height = window.innerHeight;

// === Minimal mat4 implementation ===
const mat4 = {
	perspective(out, fovy, aspect, near, far) {
		const f = 1.0 / Math.tan(fovy / 2);
		const nf = 1 / (near - far);
		out[0] = f / aspect; out[1] = 0; out[2] = 0; out[3] = 0;
		out[4] = 0; out[5] = f; out[6] = 0; out[7] = 0;
		out[8] = 0; out[9] = 0; out[10] = (far + near) * nf; out[11] = -1;
		out[12] = 0; out[13] = 0; out[14] = (2 * far * near) * nf; out[15] = 0;
		return out;
	},
	lookAt(out, eye, center, up) {
		let x0, x1, x2, y0, y1, y2, z0, z1, z2, len;
		let eyex = eye[0], eyey = eye[1], eyez = eye[2];
		let upx = up[0], upy = up[1], upz = up[2];
		let centerx = center[0], centery = center[1], centerz = center[2];

		if (Math.abs(eyex - centerx) < 0.000001 &&
				Math.abs(eyey - centery) < 0.000001 &&
				Math.abs(eyez - centerz) < 0.000001) {
			return mat4.identity(out);
		}

		z0 = eyex - centerx;
		z1 = eyey - centery;
		z2 = eyez - centerz;

		len = 1 / Math.hypot(z0, z1, z2);
		z0 *= len;
		z1 *= len;
		z2 *= len;

		x0 = upy * z2 - upz * z1;
		x1 = upz * z0 - upx * z2;
		x2 = upx * z1 - upy * z0;
		len = 1 / Math.hypot(x0, x1, x2);
		x0 *= len;
		x1 *= len;
		x2 *= len;

		y0 = z1 * x2 - z2 * x1;
		y1 = z2 * x0 - z0 * x2;
		y2 = z0 * x1 - z1 * x0;

		out[0] = x0;
		out[1] = y0;
		out[2] = z0;
		out[3] = 0;
		out[4] = x1;
		out[5] = y1;
		out[6] = z1;
		out[7] = 0;
		out[8] = x2;
		out[9] = y2;
		out[10] = z2;
		out[11] = 0;
		out[12] = -(x0 * eyex + x1 * eyey + x2 * eyez);
		out[13] = -(y0 * eyex + y1 * eyey + y2 * eyez);
		out[14] = -(z0 * eyex + z1 * eyey + z2 * eyez);
		out[15] = 1;
		return out;
	},
	identity(out) {
		out[0] = 1; out[1] = 0; out[2] = 0; out[3] = 0;
		out[4] = 0; out[5] = 1; out[6] = 0; out[7] = 0;
		out[8] = 0; out[9] = 0; out[10] = 1; out[11] = 0;
		out[12] = 0; out[13] = 0; out[14] = 0; out[15] = 1;
		return out;
	}
};

function createShader(gl, type, source) {
	const shader = gl.createShader(type);
	gl.shaderSource(shader, source);
	gl.compileShader(shader);
	return shader;
}

const vsSource = `
	attribute vec4 aPosition;
	attribute vec4 aColor;
	uniform mat4 uProjectionMatrix;
	uniform mat4 uViewMatrix;
	varying lowp vec4 vColor;
	void main(void) {
		gl_Position = uProjectionMatrix * (uViewMatrix * aPosition);
		vColor = aColor;
	}
`;

const fsSource = `
	varying lowp vec4 vColor;
	void main(void) {
		gl_FragColor = vColor;
	}
`;

const vertexShader = createShader(gl, gl.VERTEX_SHADER, vsSource);
const fragmentShader = createShader(gl, gl.FRAGMENT_SHADER, fsSource);
const shaderProgram = gl.createProgram();
gl.attachShader(shaderProgram, vertexShader);
gl.attachShader(shaderProgram, fragmentShader);
gl.linkProgram(shaderProgram);

const programInfo = {
	program: shaderProgram,
	attribLocations: {
		position: gl.getAttribLocation(shaderProgram, 'aPosition'),
		color: gl.getAttribLocation(shaderProgram, 'aColor'),
	},
	uniformLocations: {
		projectionMatrix: gl.getUniformLocation(shaderProgram, 'uProjectionMatrix'),
		viewMatrix: gl.getUniformLocation(shaderProgram, 'uViewMatrix'),
	},
};

const positions = new Float32Array([
	-1,-1, 1, 1,-1, 1, 1, 1, 1,-1, 1, 1,
	-1,-1,-1,-1, 1,-1, 1, 1,-1, 1,-1,-1,
	-1, 1,-1,-1, 1, 1, 1, 1, 1, 1, 1,-1,
	-1,-1,-1, 1,-1,-1, 1,-1, 1,-1,-1, 1,
	 1,-1,-1, 1, 1,-1, 1, 1, 1, 1,-1, 1,
	-1,-1,-1,-1,-1, 1,-1, 1, 1,-1, 1,-1,
]);

const random = seededRandom(1234);

const colors = new Float32Array(6 * 4 * 4);
for(let i=0;i<6;i++){
	for(let j=0;j<4;j++){
		const r = random()*.5+.5
		const g = random()*.5+.5
		const b = random()*.5+.5
		colors[(i*4+j)*4	] = r
		colors[(i*4+j)*4+1] = g
		colors[(i*4+j)*4+2] = b
		colors[(i*4+j)*4+3] = 1.0
	}
}

const indices = new Uint16Array([
	0,1,2, 0,2,3, 4,5,6, 4,6,7,
	8,9,10, 8,10,11, 12,13,14, 12,14,15,
 16,17,18, 16,18,19, 20,21,22, 20,22,23
]);

const positionBuffer = gl.createBuffer();
gl.bindBuffer(gl.ARRAY_BUFFER, positionBuffer);
gl.bufferData(gl.ARRAY_BUFFER, positions, gl.STATIC_DRAW);

const colorBuffer = gl.createBuffer();
gl.bindBuffer(gl.ARRAY_BUFFER, colorBuffer);
gl.bufferData(gl.ARRAY_BUFFER, colors, gl.STATIC_DRAW);

const indexBuffer = gl.createBuffer();
gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, indexBuffer);
gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, indices, gl.STATIC_DRAW);

function drawScene(projectionMatrix, viewMatrix) {
	gl.enable(gl.DEPTH_TEST);

	gl.useProgram(programInfo.program);

	gl.bindBuffer(gl.ARRAY_BUFFER, positionBuffer);
	gl.vertexAttribPointer(programInfo.attribLocations.position, 3, gl.FLOAT, false, 0, 0);
	gl.enableVertexAttribArray(programInfo.attribLocations.position);

	gl.bindBuffer(gl.ARRAY_BUFFER, colorBuffer);
	gl.vertexAttribPointer(programInfo.attribLocations.color, 4, gl.FLOAT, false, 0, 0);
	gl.enableVertexAttribArray(programInfo.attribLocations.color);

	gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, indexBuffer);

	gl.uniformMatrix4fv(programInfo.uniformLocations.projectionMatrix, false, projectionMatrix);
	gl.uniformMatrix4fv(programInfo.uniformLocations.viewMatrix, false, viewMatrix);

	gl.drawElements(gl.TRIANGLES, indices.length, gl.UNSIGNED_SHORT, 0);
}

// === Fallback rendering ===
function fallbackLoop() {
	if (hasXRSession) return;
	const time = (new Date().getTime() * 0.001) % 3600.0;
	const angle = time;
	const fov = 90 * Math.PI / 180;
	const aspect = canvas.width / canvas.height;
	const zNear = 0.1;
	const zFar = 100.0;
	const projectionMatrix = mat4.perspective([], fov, aspect, zNear, zFar);
	const eye = [Math.cos(angle) * 10, 0, Math.sin(angle) * 10];
	const viewMatrix = mat4.lookAt([], eye, [0, 0, 0], [0, 1, 0]);

	gl.viewport(0, 0, canvas.width, canvas.height);
	gl.clearColor(0.1,0.1,0.1,1.0);
	gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
	drawScene(projectionMatrix, viewMatrix);
	requestAnimationFrame(fallbackLoop);
}

function seededRandom(seed) {
	let value = seed % 2147483647;
	if (value <= 0) value += 2147483646;

	return function() {
		value = value * 16807 % 2147483647;
		return (value - 1) / 2147483646;
	};
}

function resizeCanvasToDisplaySize() {
	const dpr = window.devicePixelRatio || 1;
	const width = Math.floor(window.innerWidth * dpr);
	const height = Math.floor(window.innerHeight * dpr);

	if (canvas.width !== width || canvas.height !== height) {
		canvas.width = width;
		canvas.height = height;
		gl.viewport(0, 0, canvas.width, canvas.height);
	}
}

window.addEventListener('resize', resizeCanvasToDisplaySize);