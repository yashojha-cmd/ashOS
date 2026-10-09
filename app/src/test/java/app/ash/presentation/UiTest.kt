package app.ash.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.ash.designsystem.AshIcons
import app.ash.designsystem.AshTheme
import app.ash.designsystem.AttentionCard
import app.ash.designsystem.EmptyState
import app.ash.designsystem.StatusPill
import app.ash.designsystem.Tone
import app.ash.domain.model.Category
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** UI and accessibility tests (§27, §39): status is announced as text, cards are actionable. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun attentionCardIsReadableAndClickable() {
        var clicked = false
        compose.setContent {
            AshTheme {
                AttentionCard(
                    title = "Electricity bill",
                    detail = "₹2,140 · Due today",
                    label = "Needs attention",
                    tone = Tone.URGENT,
                    icon = AshIcons.category(Category.BILLS),
                    onClick = { clicked = true },
                )
            }
        }
        compose.onNodeWithContentDescription("Needs attention. Electricity bill. ₹2,140 · Due today")
            .assertHasClickAction()
            .performClick()
        assertThat(clicked).isTrue()
    }

    @Test fun statusIsNotColourOnly() {
        compose.setContent { AshTheme { StatusPill("Coming up", Tone.SOON) } }
        compose.onNodeWithText("Coming up").assertExists()
    }

    @Test fun emptyStateShowsMessageAndAction() {
        var tapped = false
        compose.setContent {
            AshTheme {
                EmptyState(Icons.Rounded.Inbox, "Nothing needs your attention.", "You're all caught up.", actionLabel = "Add bill", onAction = { tapped = true })
            }
        }
        compose.onNodeWithText("Nothing needs your attention.").assertExists()
        compose.onNodeWithText("Add bill").performClick()
        assertThat(tapped).isTrue()
    }
}
