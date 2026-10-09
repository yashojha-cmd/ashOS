package app.ash.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.ash.R
import app.ash.designsystem.OptionField
import app.ash.domain.model.Profile
import app.ash.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ProfilesPickerViewModel @Inject constructor(repo: ProfileRepository) : ViewModel() {
    val profiles = repo.observeProfiles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Optional "belongs to" field (§19). Null means no specific profile. */
@Composable
fun ProfilePicker(selectedId: Long?, onSelect: (Long?) -> Unit) {
    val vm: ProfilesPickerViewModel = hiltViewModel()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    if (profiles.size <= 1) return
    val options: List<Profile?> = listOf<Profile?>(null) + profiles
    val noneLabel = stringResource(R.string.profile_none)
    OptionField(
        label = stringResource(R.string.field_profile),
        options = options,
        selected = profiles.firstOrNull { it.id == selectedId },
        optionLabel = { it?.name ?: noneLabel },
        onSelect = { onSelect(it?.id) },
    )
}
