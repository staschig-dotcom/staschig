package ru.staschig.guitar.lessons

import kotlin.math.roundToInt

enum class Level(val title: String, val prefix: String) {
    BEGINNER("Начинающий", "b"),
    INTERMEDIATE("Средний", "i"),
    ADVANCED("Продвинутый", "a"),
}

/** Как делить время урока между блоками: разминка / техника / песня. */
enum class Focus(val title: String, val warmup: Float, val technique: Float, val song: Float) {
    BALANCED("Баланс", 0.15f, 0.45f, 0.40f),
    TECHNIQUE("Техника", 0.15f, 0.60f, 0.25f),
    SONGS("Песни", 0.10f, 0.30f, 0.60f),
}

enum class StepKind(val title: String) {
    WARMUP("Разминка"),
    TECHNIQUE("Базовое упражнение"),
    SONG("Разбор песни"),
}

data class Exercise(
    val id: String,
    val title: String,
    /** Школа/методика, на которую опирается упражнение. */
    val school: String,
    val description: String,
    val tab: String? = null,
    val bpmStart: Int? = null,
    val bpmTarget: Int? = null,
)

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val chords: List<String>,
    val strumming: String,
    val description: String,
    /** Собственный таб — только для народных мелодий и мелодий в общественном достоянии. */
    val tab: String? = null,
    val bpm: Int? = null,
)

data class Step(
    val kind: StepKind,
    val minutes: Int,
    val exercise: Exercise? = null,
    val song: Song? = null,
) {
    val title: String get() = exercise?.title ?: song?.let { "${it.artist} — ${it.title}" } ?: ""
    val id: String get() = exercise?.id ?: song?.id ?: ""
}

data class Lesson(
    val id: String,
    val level: Level,
    val number: Int,
    val warmup: Exercise,
    val technique: Exercise,
    val song: Song,
) {
    val title: String get() = "Урок $number. ${technique.title}"

    fun steps(dailyMinutes: Int, focus: Focus): List<Step> {
        fun share(f: Float) = maxOf(1, (dailyMinutes * f).roundToInt())
        return listOf(
            Step(StepKind.WARMUP, share(focus.warmup), exercise = warmup),
            Step(StepKind.TECHNIQUE, share(focus.technique), exercise = technique),
            Step(StepKind.SONG, share(focus.song), song = song),
        )
    }
}

object Curriculum {

    // ------------------------------------------------------------------ РАЗМИНКИ

    private val spiderTab = """
        e|-------------------------1-2-3-4-|
        B|---------------------1-2-3-4-----|
        G|-----------------1-2-3-4---------|
        D|-------------1-2-3-4-------------|
        A|---------1-2-3-4-----------------|
        E|-1-2-3-4-------------------------|
        (затем обратно вниз; сдвигайтесь на лад выше каждый проход)
    """.trimIndent()

    private val permutationTab = """
        e|-------------------------1-3-2-4-|
        B|---------------------1-3-2-4-----|
        G|-----------------1-3-2-4---------|
        D|-------------1-3-2-4-------------|
        A|---------1-3-2-4-----------------|
        E|-1-3-2-4-------------------------|
        Другие перестановки: 1-4-2-3, 2-4-1-3, 4-3-2-1
    """.trimIndent()

    private val trillTab = """
        G|-5h7p5h7p5h7p5h7-|-5h8p5h8p5h8p5h8-|
        (h — hammer-on, p — pull-off; пары пальцев 1-2, 1-3, 1-4, 2-3, 2-4, 3-4)
    """.trimIndent()

    val warmups: Map<Level, List<Exercise>> = mapOf(
        Level.BEGINNER to listOf(
            Exercise(
                "w_stretch", "Разогрев кистей без гитары", "Общая практика",
                "1 мин: вращения кистей, сжимание/разжимание кулака. 1 мин: мягкая растяжка каждого пальца. " +
                    "Затем 2 мин «паук» (см. таб) на 5-м ладу — там лады уже, пальцам легче. " +
                    "Каждый палец — на свой лад, ставьте палец прямо за ладовой порожек.",
                tab = spiderTab, bpmStart = 50, bpmTarget = 70,
            ),
            Exercise(
                "w_spider", "Хроматика «паук» 1-2-3-4", "Стив Вай, «10-Hour Workout»",
                "Играйте одну ноту на удар, строго переменным штрихом (вниз-вверх). " +
                    "Не поднимайте пальцы высоко над грифом — минимальное движение важнее скорости.",
                tab = spiderTab, bpmStart = 60, bpmTarget = 90,
            ),
            Exercise(
                "w_open", "Открытые струны и приглушение", "Justin Guitar, Grade 1",
                "Играйте каждую открытую струну по 4 раза четвертями, затем восьмыми. " +
                    "Следите, чтобы соседние струны не звенели: глушите их ладонью правой руки.",
                bpmStart = 60, bpmTarget = 80,
            ),
        ),
        Level.INTERMEDIATE to listOf(
            Exercise(
                "w_spider_mid", "«Паук» с восьмыми и триолями", "Стив Вай, «10-Hour Workout»",
                "Тот же паттерн 1-2-3-4, но 2 ноты на удар, затем 3 (триоли). Начинайте с 1-го лада — " +
                    "там растяжка максимальная. Метроном с дроблением поможет держать ровность.",
                tab = spiderTab, bpmStart = 70, bpmTarget = 110,
            ),
            Exercise(
                "w_perm", "Пальцевые перестановки", "Джон Петруччи, «Rock Discipline»",
                "Хроматика с нестандартным порядком пальцев развивает независимость. " +
                    "Каждую перестановку — по 1 минуте.",
                tab = permutationTab, bpmStart = 60, bpmTarget = 100,
            ),
            Exercise(
                "w_trill", "Трели (легато)", "Джон Петруччи, «Rock Discipline»",
                "По 20–30 секунд на каждую пару пальцев. Pull-off — это «щипок» струны пальцем вниз, " +
                    "а не просто снятие пальца. Громкость нот должна быть одинаковой.",
                tab = trillTab,
            ),
        ),
        Level.ADVANCED to listOf(
            Exercise(
                "w_perm_adv", "Перестановки шестнадцатыми", "Джон Петруччи, «Rock Discipline»",
                "Все 24 перестановки четырёх пальцев, по 4 ноты на удар. Начинайте медленно и чисто.",
                tab = permutationTab, bpmStart = 80, bpmTarget = 140,
            ),
            Exercise(
                "w_trill_adv", "Трели на выносливость", "Пол Гилберт",
                "Трель одной парой пальцев 60 секунд без остановки, затем смена пары. " +
                    "Расслабляйте кисть — напряжение убивает скорость.",
                tab = trillTab,
            ),
            Exercise(
                "w_sync", "Синхронизация рук (одна нота — шестнадцатыми)", "Troy Grady, «Cracking the Code»",
                "Повторяйте одну ноту строго переменным штрихом, затем переходите на соседнюю струну. " +
                    "Слушайте, совпадает ли атака медиатора с прижатием пальца.",
                bpmStart = 90, bpmTarget = 160,
            ),
        ),
    )

    // ------------------------------------------------------------------ ТЕХНИКА

    private val technique: Map<Level, List<Exercise>> = mapOf(
        Level.BEGINNER to listOf(
            Exercise(
                "t_posture", "Посадка, медиатор, первые звуки", "Justin Guitar, Grade 1",
                "Гитара лежит на правой ноге, гриф слегка вверх. Медиатор держите между подушечкой большого " +
                    "и боковой стороной указательного, кончик торчит на 2–3 мм. Большой палец левой руки — за грифом " +
                    "напротив среднего пальца. Сыграйте каждую струну открыто, затем на 1, 2, 3 ладу.",
                bpmStart = 60, bpmTarget = 70,
            ),
            Exercise(
                "t_ade", "Аккорды A, D, E + «одноминутные смены»", "Justin Guitar — One Minute Changes",
                "Выучите аппликатуры A, D, E. Затем засеките минуту и меняйте пару аккордов (A↔D), " +
                    "считая количество смен. Запишите результат — через неделю он должен вырасти вдвое. " +
                    "Пары: A↔D, D↔E, A↔E.",
                tab = """
                    A: x02220   D: xx0232   E: 022100
                """.trimIndent(),
            ),
            Exercise(
                "t_emcg", "Аккорды Am, Em, C, G", "Justin Guitar — One Minute Changes",
                "Новые аккорды и смены Am↔C, Em↔G, C↔G. Ставьте пальцы одновременно, а не по одному. " +
                    "Ищите «пальцы-якоря»: в Am↔C пальцы 1 и 2 остаются на месте.",
                tab = """
                    Am: x02210   Em: 022000   C: x32010   G: 320003
                """.trimIndent(),
            ),
            Exercise(
                "t_strum", "Ритм: «Old Faithful» бой", "Justin Guitar, Grade 1",
                "Рука двигается вниз-вверх постоянно, как маятник, а по струнам попадает не всегда. " +
                    "Паттерн на счёт «1 и 2 и 3 и 4 и»: ↓ . ↓ ↑ . ↑ ↓ ↑ " +
                    "Сначала на одном аккорде, затем со сменой каждые 2 такта.",
                tab = """
                    Счёт:   1   и   2   и   3   и   4   и
                    Удар:   ↓       ↓   ↑       ↑   ↓   ↑
                """.trimIndent(),
                bpmStart = 60, bpmTarget = 90,
            ),
            Exercise(
                "t_alt", "Переменный штрих на одной струне", "W. Leavitt, «A Modern Method for Guitar» (Berklee)",
                "Играйте ноты по 4 раза: вниз-вверх-вниз-вверх. Движение — от запястья, а не от локтя. " +
                    "Затем гамма до мажор в первой позиции (таб).",
                tab = """
                    e|-------------------------0-1-3-|
                    B|-----------------0-1-3---------|
                    G|-------------0-2---------------|
                    D|-------0-2-3-------------------|
                    A|---3---------------------------|
                    (C D E F G A B C D E F G — каждая нота по 2 раза)
                """.trimIndent(),
                bpmStart = 60, bpmTarget = 100,
            ),
            Exercise(
                "t_notes", "Ноты на 1-й и 2-й струнах", "M. Carcassi / F. Carulli (классическая школа)",
                "Выучите ноты: 1-я струна E(0) F(1) G(3); 2-я струна B(0) C(1) D(3). " +
                    "Играйте пальцами (апояндо: палец после щипка опирается на соседнюю струну), чередуя i и m.",
                tab = """
                    e|-------------0-1-3-3-1-0-------|
                    B|-0-1-3-3-1-0---------------3-1-|
                """.trimIndent(),
                bpmStart = 60, bpmTarget = 80,
            ),
            Exercise(
                "t_power", "Пауэр-аккорды E5, A5, D5 и приглушение ладонью", "Классическая рок-школа",
                "Пауэр-аккорд = тоника + квинта (два пальца: 1 и 3). Ребро ладони правой руки слегка " +
                    "касается струн у подставки (palm mute). Играйте восьмыми только вниз.",
                tab = """
                    E5        A5        D5        G5 (3 лад)
                    D|-2-     D|-2-     G|-2-     A|-5-
                    A|-2-     A|-0-     D|-0-     E|-3-
                    E|-0-
                """.trimIndent(),
                bpmStart = 80, bpmTarget = 120,
            ),
            Exercise(
                "t_fmaj7", "Подготовка к баррэ: малое F и Fmaj7", "Justin Guitar, Grade 2",
                "Fmaj7 (xx3210) → малое F (xx3211, указательный зажимает 2 струны). " +
                    "Смены C↔Fmaj7, затем C↔F. Указательный палец кладите чуть боком — рёбрышком.",
                tab = """
                    Fmaj7: xx3210   F (малое): xx3211   C: x32010
                """.trimIndent(),
            ),
        ),
        Level.INTERMEDIATE to listOf(
            Exercise(
                "t_barre", "Баррэ: формы E и A (F, Bm)", "Система CAGED",
                "F = форма E на 1 ладу (133211), Bm = форма Am на 2 ладу (x24432). " +
                    "Давите не силой пальца, а весом руки (тяните локоть назад). Проверяйте каждую струну отдельно.",
                tab = """
                    F:  133211     Bm: x24432
                    G:  355433     Cm: x35543
                """.trimIndent(),
            ),
            Exercise(
                "t_penta", "Минорная пентатоника, позиция 1", "Блюз/рок-школа",
                "Ля-минорная пентатоника от 5 лада. Играйте вверх-вниз переменным штрихом, затем " +
                    "секвенциями по 3 ноты. Это основа 80% рок-соло.",
                tab = """
                    e|-----------------------5-8-|
                    B|-------------------5-8-----|
                    G|---------------5-7---------|
                    D|-----------5-7-------------|
                    A|-------5-7-----------------|
                    E|-5-8-----------------------|
                """.trimIndent(),
                bpmStart = 60, bpmTarget = 120,
            ),
            Exercise(
                "t_travis", "Travis picking (переменный бас)", "Мерл Тревис / фингерстайл",
                "Большой палец играет бас на каждую долю (5-я и 4-я струны), указательный и средний — " +
                    "мелодические ноты между ними. Сначала только бас, затем добавляйте пальцы.",
                tab = """
                    C
                    e|-------0-------0-|
                    B|---1-------1-----|
                    D|-----2-------2---|
                    A|-3-------3-------|
                """.trimIndent(),
                bpmStart = 50, bpmTarget = 90,
            ),
            Exercise(
                "t_giuliani", "Арпеджио p-i-m-a", "М. Джулиани, «120 упражнений для правой руки»",
                "Классическая постановка: p — большой, i — указательный, m — средний, a — безымянный. " +
                    "Каждый палец «закреплён» за своей струной.",
                tab = """
                    Am               E
                    e|-----0-------|-----0-------|
                    B|---1---1-----|---0---0-----|
                    G|-2-------2---|-1-------1---|
                    A|0------------|-------------|
                    E|-------------|0------------|
                       p i m a m i
                """.trimIndent(),
                bpmStart = 60, bpmTarget = 100,
            ),
            Exercise(
                "t_major", "Мажорная гамма: 3 ноты на струну", "W. Leavitt (Berklee) / CAGED",
                "Соль мажор от 3 лада, по 3 ноты на струну. Обратите внимание на сдвиг позиции на " +
                    "3-й струне. Играйте переменным штрихом с метрономом.",
                tab = """
                    e|-----------------------------5-7-8-|
                    B|-----------------------5-7-8-------|
                    G|-----------------4-5-7-------------|
                    D|-----------4-5-7-------------------|
                    A|-----3-5-7-------------------------|
                    E|-3-5-7-----------------------------|
                """.trimIndent(),
                bpmStart = 60, bpmTarget = 110,
            ),
            Exercise(
                "t_mute", "Шестнадцатые и приглушение левой рукой", "Фанк-школа (Найл Роджерс)",
                "Рука непрерывно играет шестнадцатыми, левая рука то прижимает аккорд, то только касается " +
                    "струн (глухой «чк»). Акценты на 2 и 4 долю.",
                tab = """
                    Em9 (x7777x)
                    1 e и a 2 e и a 3 e и a 4 e и a
                    X x x x X x x x X x x x X x x x   (X — аккорд, x — глухой удар)
                """.trimIndent(),
                bpmStart = 70, bpmTarget = 100,
            ),
            Exercise(
                "t_legato", "Легато в пентатонике", "Джо Сатриани",
                "Только первая нота на струне берётся медиатором, остальные — hammer-on/pull-off.",
                tab = """
                    e|-------------------5h8p5----|
                    B|---------------5h8-------8p5|
                    G|-----------5h7--------------|
                    D|-------5h7------------------|
                    A|---5h7----------------------|
                """.trimIndent(),
                bpmStart = 60, bpmTarget = 100,
            ),
            Exercise(
                "t_bend", "Бенды и вибрато", "Блюзовая школа (Б.Б. Кинг)",
                "Бенд на тон: подтяните струну так, чтобы она звучала как нота на 2 лада выше " +
                    "(сравните с эталоном). Тяните тремя пальцами, а не одним.",
                tab = """
                    G|-7b9--7b9r7p5--|      эталон:  G|-9-|
                    B|-8b10----------|               B|-10-|
                    (b — бенд, r — отпустить, ~ — вибрато)
                """.trimIndent(),
            ),
        ),
        Level.ADVANCED to listOf(
            Exercise(
                "t_3nps", "Скорость: 3 ноты на струну", "Пол Гилберт / Troy Grady",
                "Гамма ля минор, 3 ноты на струну, строго переменный штрих. Используйте тренажёр скорости " +
                    "метронома: +4 BPM каждые 4 такта.",
                tab = """
                    e|-------------------------------5-7-8-|
                    B|-------------------------5-6-8-------|
                    G|-------------------4-5-7-------------|
                    D|-------------3-5-7-------------------|
                    A|-------3-5-7-------------------------|
                    E|-3-5-7-------------------------------|
                    (G A B | C D E | F G A | B C D | E F G | A B C)
                """.trimIndent(),
                bpmStart = 80, bpmTarget = 160,
            ),
            Exercise(
                "t_sweep", "Свип: арпеджио Am на 5 струнах", "Фрэнк Гамбале / Джейсон Беккер",
                "Медиатор «проваливается» через струны одним движением. Каждая нота звучит отдельно — " +
                    "снимайте палец сразу после ноты (иначе получится аккорд).",
                tab = """
                    e|-------------8-12-8-------------|
                    B|----------10---------10---------|
                    G|-------9----------------9-------|
                    D|----10-------------------10-----|
                    A|-12-------------------------12--|
                    ↓  ↓  ↓  ↓  ↓  ↑↓ ↑  ↑  ↑  ↑
                """.trimIndent(),
                bpmStart = 50, bpmTarget = 100,
            ),
            Exercise(
                "t_modes", "Лады: дорийский и миксолидийский", "Berklee — модальная импровизация",
                "Сыграйте ре дорийский (гамма до мажор от D) поверх Dm7 и соль миксолидийский поверх G7. " +
                    "Ищите характерную ноту: в дорийском — большая секста (B), в миксолидийском — малая септима (F).",
            ),
            Exercise(
                "t_jazz", "Джаз: II–V–I и шелл-аккорды", "Джо Пасс / Тед Грин",
                "Шелл-аккорды: тоника + терция + септима. Dm7 (x5x56x) → G7 (3x34xx) → Cmaj7 (x3x45x). " +
                    "Затем во всех тональностях по квартовому кругу.",
                bpmStart = 80, bpmTarget = 140,
            ),
            Exercise(
                "t_skip", "Перескок через струну", "Пол Гилберт",
                "Арпеджио с перескоком через струну — точность правой руки.",
                tab = """
                    e|-----------5-8-12-8-5-----------|
                    G|-----5-9----------------9-5-----|
                    A|-7------------------------------|
                """.trimIndent(),
                bpmStart = 60, bpmTarget = 120,
            ),
            Exercise(
                "t_tapping", "Тэппинг", "Эдди Ван Хален",
                "Пальцем правой руки (t) нажмите 12 лад и сорвите вниз на 5, затем pull-off на 8 → 5.",
                tab = """
                    B|-12t-5-8-12t-5-8-12t-5-8-|
                """.trimIndent(),
                bpmStart = 60, bpmTarget = 120,
            ),
            Exercise(
                "t_blues", "Импровизация: блюз 12 тактов в A", "Блюзовая школа",
                "Схема: A7 ×4 | D7 ×2 | A7 ×2 | E7 | D7 | A7 | E7. Включите метроном, играйте ритм-партию " +
                    "(шаффл), затем импровизируйте пентатоникой Am + блюзовая нота (Eb). " +
                    "Задача — фразы, совпадающие со сменой аккордов.",
                bpmStart = 70, bpmTarget = 110,
            ),
            Exercise(
                "t_hybrid", "Гибридный штрих (chicken picking)", "Кантри-школа (Брэд Пейсли, Альберт Ли)",
                "Медиатор играет бас, средний и безымянный пальцы — верхние струны щипком.",
                tab = """
                    e|-----0-----0-----|
                    B|-------1-----1---|
                    D|-2-------2-------|
                    A|-----------------|
                       p m a   p m a
                """.trimIndent(),
                bpmStart = 70, bpmTarget = 120,
            ),
        ),
    )

    // ------------------------------------------------------------------ ПЕСНИ

    private val odeToJoyTab = """
        Л. ван Бетховен, «Ода к радости» (до мажор)
        e|-0-0-1-3-|-3-1-0---|-------0-|-0-----------|
        B|---------|-------3-|-1-1-3---|-----3-3-----|
        (E E F G | G F E D | C C D E | E. D D)
    """.trimIndent()

    private val greensleevesTab = """
        «Greensleeves» (народная, ля минор, 3/4)
        e|-------0-1-0-----|-------------|-----------|
        B|---1-3-------3-0-|-----0-1-----|-----0-----|
        G|-2---------------|-0-2-----2-2-|-1-2---1---|
        D|-----------------|-------------|---------2-|
        (A C D E F E D B G A B C A A G# A B G# E)
    """.trimIndent()

    private val risingSunTab = """
        «House of the Rising Sun» (народная): Am C D F | Am C E E
        Арпеджио 6/8 на Am (схема для всех аккордов: бас — затем вверх и вниз):
        e|---------0-----|
        B|-------1---1---|
        G|-----2-------2-|
        D|---2-----------|
        A|-0-------------|
    """.trimIndent()

    private val romanceTab = """
        «Романс» (анонимный, ми минор) — начало, триоли a-m-i
        e|-7-----7-----7-----|-7-----5-----3-----|-3-----2-----0-----|-0-----3-----7-----|
        B|---0-----0-----0---|---0-----0-----0---|---0-----0-----0---|---0-----0-----0---|
        G|-----0-----0-----0-|-----0-----0-----0-|-----0-----0-----0-|-----0-----0-----0-|
        E|-0-----------------|-0-----------------|-0-----------------|-0-----------------|
    """.trimIndent()

    private val songs: Map<Level, List<Song>> = mapOf(
        Level.BEGINNER to listOf(
            Song(
                "s_ode", "Ода к радости", "Л. ван Бетховен", listOf("C", "G"),
                "Мелодия одиночными нотами, переменный штрих",
                "Первая мелодия на нотах 1-й и 2-й струн. Играйте ровно четвертями под метроном.",
                tab = odeToJoyTab, bpm = 70,
            ),
            Song(
                "s_knocking", "Knockin' on Heaven's Door", "Bob Dylan", listOf("G", "D", "Am", "C"),
                "По 2 удара на аккорд: ↓ ↓↑ ↑↓↑ (Old Faithful)",
                "Последовательность: G D Am | G D C — повторяется всю песню. Идеальная первая песня.",
                bpm = 68,
            ),
            Song(
                "s_horse", "A Horse with No Name", "America", listOf("Em", "D6/9"),
                "↓ ↓ ↓↑ ↑↓↑ — всё на 16-х, рука не останавливается",
                "Всего 2 аккорда: Em (022000) и D6add9/F# (2x4220). Тренирует ритм, а не смены.",
                bpm = 122,
            ),
            Song(
                "s_stand", "Stand by Me", "Ben E. King", listOf("G", "Em", "C", "D"),
                "↓ ↓↑ ↑↓↑ или перебор по басу",
                "Классическая последовательность «50-х»: G Em C D. По 2 такта на аккорд.",
                bpm = 118,
            ),
            Song(
                "s_risingsun", "House of the Rising Sun", "народная", listOf("Am", "C", "D", "F", "E"),
                "Арпеджио 6/8 (см. таб)",
                "Песня-тренажёр перебора и смены аккордов, включая первый F.",
                tab = risingSunTab, bpm = 70,
            ),
            Song(
                "s_zombie", "Zombie", "The Cranberries", listOf("Em", "C", "G", "D"),
                "↓ ↓ ↓↑ ↓↑ — мощно, с акцентом на 1 долю",
                "Em C G D (в оригинале Em7 Cmaj7 G D6/F#) на всю песню. Отработка ритма в среднем темпе.",
                bpm = 84,
            ),
            Song(
                "s_teen", "Smells Like Teen Spirit", "Nirvana", listOf("F5", "Bb5", "Ab5", "Db5"),
                "Пауэр-аккорды, ↓ ↓↑ с глухими ударами между аккордами",
                "Пауэр-аккорды на 6-й и 5-й струнах: F5 (1 лад), Bb5 (1 лад 5-й струны), Ab5 (4 лад), Db5 (4 лад 5-й).",
                bpm = 117,
            ),
            Song(
                "s_letitbe", "Let It Be", "The Beatles", listOf("C", "G", "Am", "F"),
                "Четвертями ↓ ↓ ↓ ↓, в припеве ↓ ↓↑",
                "Куплет: C G Am F | C G F C. Отличное закрепление смен с аккордом F.",
                bpm = 72,
            ),
        ),
        Level.INTERMEDIATE to listOf(
            Song(
                "s_greensleeves", "Greensleeves", "народная", listOf("Am", "G", "F", "E"),
                "Мелодия пальцами + бас на первую долю",
                "Мелодия в размере 3/4. Сначала только мелодию, затем добавьте бас на открытых струнах.",
                tab = greensleevesTab, bpm = 80,
            ),
            Song(
                "s_wonderwall", "Wonderwall", "Oasis", listOf("Em7", "G", "Dsus4", "A7sus4"),
                "16-е с акцентами, каподастр на 2 лад",
                "Em7 (022033), G (320033), Dsus4 (xx0233), A7sus4 (x02033) — мизинец и безымянный не двигаются.",
                bpm = 87,
            ),
            Song(
                "s_heyjoe", "Hey Joe", "Jimi Hendrix", listOf("C", "G", "D", "A", "E"),
                "↓ ↓↑ ↑↓↑ + басовые проходы",
                "Последовательность по квинтовому кругу: C G D A E. Пробуйте баррэ-версии.",
                bpm = 84,
            ),
            Song(
                "s_wish", "Wish You Were Here", "Pink Floyd", listOf("C", "D/F#", "Am", "G"),
                "Бой с акцентами на басу",
                "Куплет: C D/F# Am G D/F# C Am G. Внимание на басовую ноту F# большим пальцем.",
                bpm = 61,
            ),
            Song(
                "s_nothing", "Nothing Else Matters", "Metallica", listOf("Em", "D", "C"),
                "Перебор пальцами по открытым струнам",
                "Вступление — арпеджио Em на открытых струнах. Куплет: Em D C. Тренирует перебор и баррэ-формы.",
                bpm = 69,
            ),
            Song(
                "s_alabama", "Sweet Home Alabama", "Lynyrd Skynyrd", listOf("D", "C", "G"),
                "Аккордовый рифф с добавочными нотами",
                "D C G — рифф строится на аккордах с украшениями (hammer-on, добавленные ноты).",
                bpm = 98,
            ),
            Song(
                "s_hallelujah", "Hallelujah", "Leonard Cohen", listOf("C", "Am", "F", "G", "E"),
                "Арпеджио 6/8: бас — 3 — 2 — 1 — 2 — 3",
                "Куплет: C Am C Am F G C G. Упражнение на размер 6/8 и ровный перебор.",
                bpm = 60,
            ),
            Song(
                "s_romance", "Романс", "анонимный", listOf("Em", "B7", "Am"),
                "Триоли a-m-i",
                "Классика фингерстайла. Мелодию ведёт безымянный палец, звучит громче аккомпанемента.",
                tab = romanceTab, bpm = 60,
            ),
        ),
        Level.ADVANCED to listOf(
            Song(
                "s_hotel", "Hotel California", "Eagles", listOf("Bm", "F#", "A", "E", "G", "D", "Em"),
                "Арпеджио + баррэ, финальное соло с гармонизацией в терцию",
                "Последовательность: Bm F# A E G D Em F#. Разбор финального соло — отличная школа фразировки.",
                bpm = 74,
            ),
            Song(
                "s_stairway", "Stairway to Heaven", "Led Zeppelin", listOf("Am", "Am/G#", "C/G", "D/F#", "Fmaj7", "G"),
                "Фингерстайл с хроматическим басом",
                "Вступление — нисходящий хроматический бас под арпеджио. Соло — пентатоника Am.",
                bpm = 72,
            ),
            Song(
                "s_dust", "Dust in the Wind", "Kansas", listOf("C", "Cmaj7", "Cadd9", "Asus2", "Asus4", "Am"),
                "Travis picking",
                "Переменный бас большим пальцем + смена «украшений» аккорда безымянным и мизинцем.",
                bpm = 98,
            ),
            Song(
                "s_blackbird", "Blackbird", "The Beatles", listOf("G", "Am7", "G/B", "C", "D"),
                "Щипок «большой + средний» одновременно",
                "Параллельные децимы, размер меняется по ходу. Высший пилотаж фингерстайла для любителя.",
                bpm = 94,
            ),
            Song(
                "s_backinblack", "Back in Black", "AC/DC", listOf("E5", "D5", "A5"),
                "Рифф с паузами — глушение обеими руками",
                "E5 D5 A5 + пентатонические связки. Главное — тишина между аккордами.",
                bpm = 94,
            ),
            Song(
                "s_hotel_solo", "Hotel California — соло", "Eagles", listOf("Bm"),
                "Соло: бенды, двойные ноты, арпеджио",
                "Выучите по фразам (по 2 такта), каждую — до чистого исполнения под метроном.",
                bpm = 74,
            ),
            Song(
                "s_greensleeves_adv", "Greensleeves — аранжировка с басом", "народная",
                listOf("Am", "G", "F", "E"),
                "Классический фингерстайл: бас p + мелодия a/m",
                "Сделайте собственную аранжировку: мелодия из таба + бас на каждую сильную долю.",
                tab = greensleevesTab, bpm = 90,
            ),
            Song(
                "s_romance_full", "Романс — полностью", "анонимный", listOf("Em", "B7", "Am", "E", "B7"),
                "Триоли a-m-i, вторая часть в ми мажоре с баррэ",
                "Вторая часть требует баррэ на 9 ладу и растяжек. Найдите полный таб в библиотеке.",
                tab = romanceTab, bpm = 70,
            ),
        ),
    )

    // ------------------------------------------------------------------ УРОКИ

    val lessons: List<Lesson> = Level.entries.flatMap { level ->
        val w = warmups.getValue(level)
        val t = technique.getValue(level)
        val s = songs.getValue(level)
        t.indices.map { i ->
            Lesson(
                id = "${level.prefix}${i + 1}",
                level = level,
                number = i + 1,
                warmup = w[i % w.size],
                technique = t[i],
                song = s[i % s.size],
            )
        }
    }

    /** Интерактивные табы (alphaTex в assets/alphatab/songs) для упражнений и песен уроков. */
    private val interactive: Map<String, String> = mapOf(
        "w_stretch" to "ex_spider", "w_spider" to "ex_spider", "w_spider_mid" to "ex_spider",
        "w_perm" to "ex_permutation", "w_perm_adv" to "ex_permutation",
        "t_alt" to "ex_c_major", "t_penta" to "ex_penta", "t_travis" to "ex_travis",
        "t_giuliani" to "ex_giuliani", "t_major" to "ex_g_major_3nps", "t_legato" to "ex_legato",
        "t_3nps" to "ex_a_minor_3nps", "t_sweep" to "ex_sweep", "t_skip" to "ex_string_skip",
        "s_ode" to "ode_to_joy", "s_greensleeves" to "greensleeves", "s_greensleeves_adv" to "greensleeves",
        "s_risingsun" to "rising_sun", "s_romance" to "romance", "s_romance_full" to "romance",
    )

    fun interactiveTab(id: String): String? = interactive[id]

    data class BuiltInPiece(val asset: String, val title: String, val artist: String, val level: Level)

    /** Встроенный песенник: мелодии в общественном достоянии с нотами, табом и звуком. */
    val builtInPieces: List<BuiltInPiece> = listOf(
        BuiltInPiece("ode_to_joy", "Ода к радости", "Л. ван Бетховен", Level.BEGINNER),
        BuiltInPiece("amazing_grace", "Amazing Grace", "народная", Level.BEGINNER),
        BuiltInPiece("rising_sun", "House of the Rising Sun — перебор", "народная", Level.BEGINNER),
        BuiltInPiece("greensleeves", "Greensleeves", "народная", Level.INTERMEDIATE),
        BuiltInPiece("fur_elise", "К Элизе (начало)", "Л. ван Бетховен", Level.INTERMEDIATE),
        BuiltInPiece("canon", "Канон ре мажор — перебор", "И. Пахельбель", Level.INTERMEDIATE),
        BuiltInPiece("romance", "Романс (начало)", "анонимный", Level.INTERMEDIATE),
    )

    fun byLevel(level: Level): List<Lesson> = lessons.filter { it.level == level }

    fun byId(id: String): Lesson? = lessons.firstOrNull { it.id == id }

    /** Ссылка на поиск табов песни (сайт можно поменять в одном месте). */
    const val TABS_LIBRARY_URL = "https://guitarmaestro.ru/free-tabs-library/"

    fun searchUrl(song: Song): String =
        "https://guitarmaestro.ru/?s=" + java.net.URLEncoder.encode("${song.artist} ${song.title}", "UTF-8")
}
