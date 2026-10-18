package com.greenguardians.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import com.greenguardians.app.data.Json
import com.greenguardians.app.model.L
import com.greenguardians.app.model.UserProgress
import com.greenguardians.app.ui.ErrorLine
import com.greenguardians.app.ui.rememberApi
import com.greenguardians.app.ui.t
import com.greenguardians.app.viewmodel.MainViewModel
import kotlinx.coroutines.launch

private class Voucher(val id: String, val emoji: String, val title: L, val partner: L, val cost: Int)

private val vouchers = listOf(
    Voucher("coffee", "☕", L("20% reducere la cafea în recipient reutilizabil", "20% off coffee in a reusable cup"), L("Cafenea Specialty Iași", "Specialty Café Iași"), 150),
    Voucher("bike", "🚲", L("Revizie gratuită bicicletă / voucher accesorii", "Free bike service / accessories voucher"), L("Bike Hub Moldova", "Bike Hub Moldova"), 300),
    Voucher("zerowaste", "🥗", L("Voucher 25 Lei la produse Zero-Waste", "25 RON voucher for Zero-Waste products"), L("Magazin Eco Local", "Local Eco Store"), 250),
    Voucher("cinema", "🎬", L("Bilet redus la evenimente comunitare / cinema", "Discounted community event / cinema ticket"), L("Partener Suceava & Bacău", "Partner Suceava & Bacău"), 200),
)

private class Revenue(val title: L, val price: L, val desc: L)

private val revenues = listOf(
    Revenue(L("1. Abonamente Companii (prioritatea #1)", "1. Company subscriptions (priority #1)"), L("300–1.500 lei/lună/companie", "300–1,500 RON/month/company"),
        L("Competiții între angajați și rapoarte automate de impact ESG / CO₂.", "Employee competitions and automatic ESG / CO₂ impact reports.")),
    Revenue(L("2. Misiuni sponsorizate", "2. Sponsored quests"), L("500–3.000 lei/campanie", "500–3,000 RON/campaign"),
        L("Provocări finanțate de supermarketuri sau branduri (ex: „Săptămâna Reciclării”).", "Challenges funded by supermarkets or brands (e.g. “Recycling Week”).")),
    Revenue(L("3. Parteneriate cu magazine locale", "3. Local store partnerships"), L("100–300 lei/lună sau comision", "100–300 RON/month or commission"),
        L("Listare de vouchere pe EcoCoins.", "Voucher listings paid in EcoCoins.")),
    Revenue(L("4. Competiții pentru școli și universități", "4. School & university competitions"), L("1.000–5.000 lei/campanie sponsorizată", "1,000–5,000 RON/sponsored campaign"),
        L("Școlile participă gratuit, sponsorii finanțează premiile.", "Schools join for free, sponsors fund the prizes.")),
    Revenue(L("5. EcoPremium pentru utilizatori", "5. EcoPremium for users"), L("9,99–14,99 lei/lună", "9.99–14.99 RON/month"),
        L("Statistici avansate, analize AI extinse, personalizare profil. Fără puncte EP cumpărate.", "Advanced stats, extended AI analysis, profile customization. No EP can be bought.")),
    Revenue(L("6. Contracte cu primării și ONG-uri", "6. City hall & NGO contracts"), L("Contract per campanie", "Per-campaign contract"),
        L("Campanii locale de transport verde și reciclare.", "Local green transport and recycling campaigns.")),
)

@Composable
fun ImpactScreen(vm: MainViewModel, p: UserProgress) {
    var tab by remember { mutableStateOf(0) }
    val titles = listOf(L("🐻‍❄️ Impact", "🐻‍❄️ Impact"), L("🛍️ Shop", "🛍️ Shop"), L("🤝 B2B2C", "🤝 B2B2C"))
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TabRow(selectedTabIndex = tab) {
                titles.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t.t()) }) }
            }
        }
        when (tab) {
            0 -> impact(vm, p)
            1 -> shop(vm, p)
            else -> partners()
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.impact(vm: MainViewModel, p: UserProgress) {
    item {
        val anim by animateFloatAsState(p.polarBearProgress, label = "bear")
        val bearNo = p.polarBearsSaved.toInt() + 1
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0B2A3A))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("❄️ " + L("Sanctuarul Arctic Green Guardians", "Green Guardians Arctic Sanctuary").t(), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("%.2f ".format(p.polarBearsSaved) + L("Urși Polari Salvați 🐻‍❄️", "Polar Bears Saved 🐻‍❄️").t(), color = Color.White, style = MaterialTheme.typography.headlineSmall)
                LinearProgressIndicator(progress = { anim }, Modifier.fillMaxWidth(), color = Color(0xFF7DD3FC))
                Text("Nanuq #$bearNo · ${(p.polarBearProgress * 100).toInt()}%", color = Color(0xFFBAE6FD))
                Text(
                    L("🧊 Gheață arctică salvată: %.1f cm² (%.4f m²)".format(p.arcticIceSavedCm2, p.arcticIceSavedM2), "🧊 Arctic ice saved: %.1f cm² (%.4f m²)".format(p.arcticIceSavedCm2, p.arcticIceSavedM2)).t(),
                    color = Color.White,
                )
                Text(L("≈ %.1f cuburi de gheață, cu latura de 3 cm".format(p.iceCubeFaceEquivalents),
                    "≈ %.1f ice cubes with 3 cm sides".format(p.iceCubeFaceEquivalents)).t(), color = Color(0xFFBAE6FD), fontWeight = FontWeight.Bold)
                Text(L("Echivalent vizual: numărăm fețe de 3 × 3 cm (9 cm²). Suprafața salvată nu ne spune volumul gheții.",
                    "Visual equivalent: we count 3 × 3 cm faces (9 cm²). Saved surface area does not measure ice volume.").t(), color = Color(0xFFBAE6FD), style = MaterialTheme.typography.bodySmall)
                Text(
                    L("🍽️ Zile de vânătoare și hrănire asigurate: %.1f".format(p.habitatDays), "🍽️ Hunting & feeding days secured: %.1f".format(p.habitatDays)).t(),
                    color = Color.White,
                )
            }
        }
    }
    item {
        val community by rememberApi(p.verifiedQuestEP) { vm.api.get("/api/community") }
        val c = community?.getOrNull()
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(L("Comunitatea Green Guardians (Moldova)", "Green Guardians community (Moldova)").t(), fontWeight = FontWeight.Bold)
                if (c == null) {
                    community?.exceptionOrNull()?.let { ErrorLine(it) } ?: CircularProgressIndicator()
                } else {
                    Text("%.1f ".format(c.getDouble("polarBears")) + L("Urși Polari Salvați împreună", "Polar Bears Saved together").t())
                    Text("%.2f m² ".format(c.getDouble("iceM2")) + L("de gheață arctică protejată", "of Arctic ice protected").t())
                    Text(L("${c.getInt("users")} Green Guardians", "${c.getInt("users")} Green Guardians").t(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    item {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(L("Echivalențe", "Equivalents").t(), fontWeight = FontWeight.Bold)
                Text(L("🚗 %.1f km cu mașina evitați".format(p.carKmAvoided), "🚗 %.1f car km avoided".format(p.carKmAvoided)).t())
                Text(L("🌳 %.2f copaci maturi într-un an".format(p.treeEquivalents), "🌳 %.2f mature trees in a year".format(p.treeEquivalents)).t())
                Text(L("♻️ ${p.sgrReturned} ambalaje SGR returnate", "♻️ ${p.sgrReturned} SGR packages returned").t())
                Text(L("🌍 %.2f kg CO₂ economisiți (verificat)".format(p.verifiedCO2SavedKg), "🌍 %.2f kg CO₂ saved (verified)".format(p.verifiedCO2SavedKg)).t())
            }
        }
    }
    item {
        Text(
            L(
                "Obiceiurile tale anterioare ți-au oferit punctele de start în Ligă (initialSelfDeclaredEP), iar acest dashboard măsoară exclusiv CO₂-ul suplimentar economisit prin misiunile tale verificate în Green Guardians.",
                "Your previous habits gave you your starting points in the League (initialSelfDeclaredEP), while this dashboard measures only the additional CO₂ saved through your verified Green Guardians quests.",
            ).t(),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.shop(vm: MainViewModel, p: UserProgress) {
    item {
        Text("🪙 EcoCoins: ${p.ecoCoins}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            L("Gratuit, fără reclame obligatorii, fără vânzarea datelor. EP nu se pot cumpăra cu bani.", "Free, no forced ads, no data selling. EP cannot be bought with money.").t(),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    item {
        val data by rememberApi(p.ecoCoins) { vm.api.get("/api/vouchers") }
        val redeemed = data?.getOrNull()?.getJSONArray("redeemed")?.let { Json.objects(it) }.orEmpty()
        if (redeemed.isNotEmpty()) {
            Text(L("Voucherele tale", "Your vouchers").t(), fontWeight = FontWeight.Bold)
            redeemed.forEach { r ->
                val v = vouchers.firstOrNull { it.id == r.getString("voucherId") }
                Text("🎟️ ${r.getString("code")} · ${v?.title?.t() ?: r.getString("voucherId")}")
            }
        }
    }
    items(vouchers) { v ->
        val scope = rememberCoroutineScope()
        var code by remember { mutableStateOf<String?>(null) }
        var error by remember { mutableStateOf<Throwable?>(null) }
        error?.let { ErrorLine(it) }
        Card {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(v.emoji, style = MaterialTheme.typography.headlineMedium)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(v.title.t(), fontWeight = FontWeight.SemiBold)
                    Text(v.partner.t(), style = MaterialTheme.typography.bodySmall)
                    code?.let { Text("🎟️ $it", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                }
                Button(
                    enabled = p.ecoCoins >= v.cost,
                    onClick = {
                        error = null
                        scope.launch { runCatching { vm.redeem(v.id) }.onSuccess { code = it }.onFailure { error = it } }
                    },
                ) { Text("${v.cost} 🪙") }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.partners() {
    item {
        Text(L("Parteneri & Monetizare B2B2C", "Partners & B2B2C Monetization").t(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
    items(revenues) { r ->
        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(r.title.t(), fontWeight = FontWeight.Bold)
                Text(r.price.t(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Text(r.desc.t(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
