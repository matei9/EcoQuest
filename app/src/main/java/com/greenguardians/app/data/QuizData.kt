package com.greenguardians.app.data

import com.greenguardians.app.model.L

class QuizOption(val text: L, val pts: Int)
class QuizQuestion(val category: Int, val text: L, val options: List<QuizOption>)
class QuizCategory(val name: L, val max: Int)

/** EcoBaseline: 10 questions, 5 categories, 100 raw points -> x25 = max 2.500 EP. */
object QuizData {
    val categories = listOf(
        QuizCategory(L("Transport", "Transport"), 30),
        QuizCategory(L("Energie și apă", "Energy & Water"), 25),
        QuizCategory(L("Alimentație și risipă", "Food & Waste"), 20),
        QuizCategory(L("Reciclare și reutilizare", "Recycling & Reuse"), 15),
        QuizCategory(L("Cumpărături și consum", "Shopping & Consumption"), 10),
    )

    private fun o(ro: String, en: String, pts: Int) = QuizOption(L(ro, en), pts)

    val questions = listOf(
        QuizQuestion(
            0,
            L("Cum te deplasezi cel mai des la școală sau la serviciu?", "How do you most often commute to school or work?"),
            listOf(
                o("Pe jos / bicicletă", "Walking / cycling", 15),
                o("Transport public", "Public transport", 12),
                o("Mașină împărțită cu alte persoane", "Carpooling", 7),
                o("Mașină personală, de obicei singur", "Personal car, usually alone", 0),
            ),
        ),
        QuizQuestion(
            0,
            L(
                "Câte zile pe săptămână înlocuiești o deplasare cu mașina printr-o alternativă ecologică?",
                "How many days a week do you replace a car trip with an eco-friendly alternative?",
            ),
            listOf(
                o("5–7 zile", "5–7 days", 15),
                o("3–4 zile", "3–4 days", 10),
                o("1–2 zile", "1–2 days", 5),
                o("Niciodată", "Never", 0),
            ),
        ),
        QuizQuestion(
            1,
            L("Cât de des ai grijă să reduci consumul inutil de electricitate?", "How often do you make sure to reduce unnecessary electricity consumption?"),
            listOf(
                o("Aproape întotdeauna", "Almost always", 10),
                o("Destul de des", "Quite often", 7),
                o("Ocazional", "Occasionally", 3),
                o("Aproape niciodată", "Almost never", 0),
            ),
        ),
        QuizQuestion(
            1,
            L("Cât durează în general un duș?", "How long does your shower usually last?"),
            listOf(
                o("Sub 5 minute", "Under 5 minutes", 10),
                o("5–10 minute", "5–10 minutes", 7),
                o("10–15 minute", "10–15 minutes", 3),
                o("Peste 15 minute", "Over 15 minutes", 0),
            ),
        ),
        QuizQuestion(
            1,
            L(
                "Cât de des folosești eficient electrocasnicele, de exemplu spălând cu încărcătură completă?",
                "How often do you use appliances efficiently, e.g., washing with a full load?",
            ),
            listOf(
                o("Aproape întotdeauna", "Almost always", 5),
                o("Uneori", "Sometimes", 3),
                o("Rar", "Rarely", 0),
            ),
        ),
        QuizQuestion(
            2,
            L("Cât de des se aruncă mâncare care putea fi consumată în gospodăria ta?", "How often is edible food thrown away in your household?"),
            listOf(
                o("Aproape niciodată", "Almost never", 10),
                o("O dată pe săptămână", "Once a week", 7),
                o("De câteva ori pe săptămână", "A few times a week", 3),
                o("Aproape zilnic", "Almost daily", 0),
            ),
        ),
        QuizQuestion(
            2,
            L(
                "Cât de des alegi mese pe bază de plante în locul unor mese cu carne cu impact ridicat?",
                "How often do you choose plant-based meals over high-impact meat meals?",
            ),
            listOf(
                o("Frecvent", "Frequently", 10),
                o("Uneori", "Sometimes", 6),
                o("Rar", "Rarely", 2),
                o("Niciodată", "Never", 0),
            ),
        ),
        QuizQuestion(
            3,
            L("Cât de des colectezi separat deșeurile reciclabile?", "How often do you separate recyclable waste?"),
            listOf(
                o("Aproape întotdeauna", "Almost always", 10),
                o("Deseori", "Often", 7),
                o("Uneori", "Sometimes", 3),
                o("Niciodată", "Never", 0),
            ),
        ),
        QuizQuestion(
            3,
            L("Folosești obiecte reutilizabile, precum sticle, sacoșe sau recipiente?", "Do you use reusable items such as bottles, bags, or containers?"),
            listOf(
                o("Aproape zilnic", "Almost daily", 5),
                o("Uneori", "Sometimes", 3),
                o("Rar", "Rarely", 0),
            ),
        ),
        QuizQuestion(
            4,
            L(
                "Cât de des repari, reutilizezi sau cumperi second-hand în loc să cumperi un produs nou?",
                "How often do you repair, reuse, or buy second-hand instead of buying new?",
            ),
            listOf(
                o("Frecvent", "Frequently", 10),
                o("Uneori", "Sometimes", 6),
                o("Rar", "Rarely", 2),
                o("Niciodată", "Never", 0),
            ),
        ),
    )
}
