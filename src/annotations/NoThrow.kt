package annotations

/**
 * Functions annotated with this guarantee, that they won't throw.
 * If they throw anyway, the program will panic and crash.
 * */
annotation class NoThrow