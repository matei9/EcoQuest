package com.greenguardians.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import android.os.Build
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.greenguardians.app.R
import com.greenguardians.app.ui.components.LangDarkToggles
import com.greenguardians.app.ui.components.EcoGlyph
import com.greenguardians.app.ui.screens.AiScreen
import com.greenguardians.app.ui.screens.AuthScreen
import com.greenguardians.app.ui.screens.ImpactScreen
import com.greenguardians.app.ui.screens.LeaguesScreen
import com.greenguardians.app.ui.screens.MissionsScreen
import com.greenguardians.app.ui.screens.ProfileScreen
import com.greenguardians.app.ui.screens.QuizScreen
import com.greenguardians.app.ui.screens.TriviaScreen
import com.greenguardians.app.ui.screens.TreeScreen
import com.greenguardians.app.viewmodel.MainViewModel

private enum class Tab(val route: String, @StringRes val label: Int, val glyph: String) {
    MISSIONS("missions", R.string.nav_missions, "home"),
    TREE("tree", R.string.nav_tree, "tree"),
    AI("ai", R.string.nav_ai, "ai"),
    LEAGUES("leagues", R.string.nav_leagues, "trophy"),
    IMPACT("impact", R.string.nav_impact, "leaf"),
    PROFILE("profile", R.string.nav_profile, "profile"),
}

private fun NavHostController.goTo(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav(vm: MainViewModel) {
    val session by vm.session.collectAsStateWithLifecycle()
    val retake by vm.retakeQuiz.collectAsStateWithLifecycle()
    val monet by vm.monetColors.collectAsStateWithLifecycle()
    val themedLogo = monet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    if (!session.loaded) return // wait for DataStore so screens don't flash
    if (session.token == null) return AuthScreen(vm)
    val me = session.me ?: return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    val p = me.progress
    if (!p.quizDone || retake) return QuizScreen(vm)

    val nav = rememberNavController()
    val current = nav.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                                .background(if (themedLogo) MaterialTheme.colorScheme.primaryContainer else colorResource(R.color.gg_icon_bg)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(if (themedLogo) R.drawable.ic_launcher_monochrome else R.drawable.ic_launcher_foreground),
                                colorFilter = if (themedLogo) ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer) else null,
                                contentDescription = null,
                                modifier = Modifier.requiredSize(48.dp),
                            )
                        }
                        Text(
                            stringResource(R.string.app_name),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = { LangDarkToggles(vm) },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = { nav.goTo(tab.route) },
                        icon = { EcoGlyph(tab.glyph, tint = if (current == tab.route) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant) },
                        label = { Text(stringResource(tab.label), maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall, overflow = TextOverflow.Clip) },
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = Tab.MISSIONS.route, modifier = Modifier.padding(pad)) {
            composable(Tab.MISSIONS.route) { MissionsScreen(vm, p) { nav.navigate("trivia") } }
            composable("trivia") { TriviaScreen(vm) { nav.popBackStack() } }
            composable(Tab.TREE.route) { TreeScreen(vm, p) { nav.goTo(Tab.MISSIONS.route) } }
            composable(Tab.AI.route) { AiScreen(vm, p) }
            composable(Tab.LEAGUES.route) { LeaguesScreen(vm, me) { nav.goTo(Tab.MISSIONS.route) } }
            composable(Tab.IMPACT.route) { ImpactScreen(vm, p) }
            composable(Tab.PROFILE.route) { ProfileScreen(vm, me) }
        }
    }
}
