package app.ash.designsystem

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry

/**
 * Spring-based motion everywhere (no linear tweens), tuned to feel like iOS push navigation:
 * the new screen slides fully in, the old one parallaxes a third of the way out.
 */
object AshMotion {
    val pushSpring = spring<IntOffset>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 420f)
    val fadeSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    val pressSpring = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(pushSpring) { it } + fadeIn(fadeSpring, initialAlpha = 0.6f)
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(pushSpring) { -it / 3 } + fadeOut(fadeSpring, targetAlpha = 0.7f)
    }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(pushSpring) { -it / 3 } + fadeIn(fadeSpring, initialAlpha = 0.7f)
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(pushSpring) { it } + fadeOut(fadeSpring, targetAlpha = 0.6f)
    }
    val tabEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(fadeSpring) + scaleIn(spring(stiffness = Spring.StiffnessMediumLow), initialScale = 0.985f)
    }
    val tabExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(fadeSpring) + scaleOut(spring(stiffness = Spring.StiffnessMediumLow), targetScale = 0.985f)
    }
}
