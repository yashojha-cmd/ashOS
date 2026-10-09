package app.ash.presentation.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesomeMosaic
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ash.R
import app.ash.designsystem.PrimaryButton
import kotlinx.coroutines.launch

private data class Page(val title: Int, val body: Int, val icon: ImageVector?)

private val pages = listOf(
    Page(R.string.onb1_title, R.string.onb1_body, null),
    Page(R.string.onb2_title, R.string.onb2_body, Icons.Rounded.AutoAwesomeMosaic),
    Page(R.string.onb3_title, R.string.onb3_body, Icons.Rounded.PhonelinkLock),
    Page(R.string.onb4_title, R.string.onb4_body, Icons.Rounded.ToggleOn),
)

/** Four short screens (§36). Skip is always visible; nothing is requested here. */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onFinished) { Text(stringResource(R.string.action_skip)) }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            val page = pages[index]
            Column(
                Modifier.fillMaxSize().padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(132.dp).clip(RoundedCornerShape(36.dp)).background(
                        if (page.icon == null) app.ash.designsystem.BrandGraphite else MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    if (page.icon == null) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = stringResource(R.string.cd_ash_logo), modifier = Modifier.size(132.dp))
                    } else {
                        Icon(page.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                    }
                }
                Spacer(Modifier.height(40.dp))
                Text(
                    stringResource(page.title),
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(page.body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                val selected = i == pager.currentPage
                val width by animateDpAsState(if (selected) 22.dp else 8.dp, label = "dot")
                val color by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "dotColor",
                )
                Box(Modifier.padding(horizontal = 4.dp).height(8.dp).width(width).clip(CircleShape).background(color))
            }
        }
        PrimaryButton(
            text = stringResource(if (last) R.string.action_get_started else R.string.action_next),
            onClick = { if (last) onFinished() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}
