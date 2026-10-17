package com.greenguardians.app.model

enum class QuestKind(val label: L) {
    DAILY(L("Zilnic", "Daily")),
    WEEKLY(L("Săptămânal", "Weekly")),
    MONTHLY(L("Lunar", "Monthly")),
}

enum class Difficulty(val label: L) {
    EASY(L("Ușor", "Easy")),
    MEDIUM(L("Mediu", "Medium")),
    HARD(L("Greu", "Hard")),
}

enum class SheetType { PHOTO, TIMER, GPS, CONFIRM, AUTO }

enum class Verification(val label: L, val sheet: SheetType) {
    GPS(L("GPS", "GPS"), SheetType.GPS),
    RECEIPT_AI(L("Bon / foto AI", "Receipt / photo AI"), SheetType.PHOTO),
    PHOTO_AI(L("Foto AI", "Photo AI"), SheetType.PHOTO),
    PHOTO(L("Foto / confirmare", "Photo / confirm"), SheetType.PHOTO),
    QR_PHOTO(L("QR / foto", "QR / photo"), SheetType.PHOTO),
    BEFORE_AFTER(L("Foto înainte/după", "Before/after photo"), SheetType.PHOTO),
    BILL(L("Factură / contor", "Bill / meter"), SheetType.PHOTO),
    TIMER(L("Timer", "Timer"), SheetType.TIMER),
    CONFIRM(L("Confirmare", "Confirm"), SheetType.CONFIRM),
    JOURNAL(L("Jurnal", "Journal"), SheetType.CONFIRM),
    AUTO(L("Automat", "Automatic"), SheetType.AUTO),
}

enum class Category { TRANSPORT, SGR, FOOD, ENERGY, REUSE, COMMUNITY, PROGRESS }

data class Quest(
    val id: String,
    val kind: QuestKind,
    val title: L,
    val difficulty: Difficulty,
    val ep: Int,
    val verification: Verification,
    val co2Kg: Double,
    val category: Category,
    val sgr: Int = 0,
    /** Simulated GPS distance for the demo route. */
    val km: Double = 3.0,
    /** AUTO quests: count completions of this kind inside the quest's own period. */
    val autoOf: QuestKind? = null,
    val autoTarget: Int = 0,
) {
    val coins: Int get() = (ep * Impact.COINS_PER_EP).toInt()
    val iceCm2: Double get() = co2Kg * 30.0
}
