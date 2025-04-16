package annotations

/**
 * Implementation for WASM and JavaScript targets.
 * If a @PureJavaScript annotation is present, this annotation will be ignored for the JavaScript target.
 * */
annotation class JavaScript(val code: String)
