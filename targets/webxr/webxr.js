let xrSession = null;
let xrRefSpace = null;

// === WebXR ===
function onXRFrame(time, frame) {
	const session = frame.session;
	session.requestAnimationFrame(onXRFrame);

	const pose = frame.getViewerPose(xrRefSpace);
	if (!pose) return;

	gl.bindFramebuffer(gl.FRAMEBUFFER, glLayer.framebuffer);
	gl.clearColor(0.1,0.1,0.1,1.0);
	gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
	for (const view of pose.views) {
		const viewport = glLayer.getViewport(view);
		gl.viewport(viewport.x, viewport.y, viewport.width, viewport.height);
		drawScene(view.projectionMatrix, view.transform.inverse.matrix);
	}
}

function onSessionStarted(session) {
	xrSession = session;
	gl.makeXRCompatible().then(() => {
		glLayer = new XRWebGLLayer(xrSession, gl);
		xrSession.updateRenderState({ baseLayer: glLayer });
		xrSession.requestReferenceSpace('local').then(refSpace => {
			xrRefSpace = refSpace;
			hasXRSession = true;
			xrSession.requestAnimationFrame(onXRFrame);
		});
	});
}

document.getElementById('enter-vr').addEventListener('click', () => {
	if (navigator.xr) {
		navigator.xr.requestSession('immersive-vr')
			.then(onSessionStarted)
			.catch(() => {
				console.error('WebXR-VR not supported');
				hasXRSession = false;
				fallbackLoop();
			});
	} else confirm('WebXR not supported')
});

// === Start fallback if XR isn't active ===
fallbackLoop();