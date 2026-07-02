package com.example.util

import java.util.Calendar
import kotlin.random.Random

object MexicanIdentityGenerator {

    data class StateConfig(
        val name: String,
        val codeCURP: String,
        val ladas: List<String>,
        val cpRanges: List<IntRange>,
        val addresses: List<String>
    )

    data class GeneratedIdentity(
        val name: String,
        val paternalLastName: String,
        val maternalLastName: String,
        val firstName: String,
        val curp: String,
        val rfc: String,
        val phone: String,
        val address: String,
        val postalCode: String,
        val stateName: String,
        val gender: String,
        val birthDateStr: String
    )

    val states = listOf(
        StateConfig(
            name = "Jalisco",
            codeCURP = "JC",
            ladas = listOf("33", "378", "391", "392", "341"),
            cpRanges = listOf(44100..44999, 45000..45239, 47000..47270, 48300..48399),
            addresses = listOf(
                "Av. Chapultepec #230, Col. Americana, Guadalajara",
                "Calle Juárez #45, Col. Centro, Zapopan",
                "Av. Vallarta #1200, Col. Americana, Guadalajara",
                "Paseo de los Volcanes #14, Col. Bugambilias, Zapopan",
                "Calle Independencia #88, Tlaquepaque Centro, San Pedro Tlaquepaque",
                "Av. Patria #1500, Col. Jardines de la Patria, Zapopan",
                "Calle Hidalgo #340, Sector Juárez, Guadalajara",
                "Paseo de las Garzas #120, Col. Las Glorias, Puerto Vallarta"
            )
        ),
        StateConfig(
            name = "Ciudad de México",
            codeCURP = "DF",
            ladas = listOf("55", "56"),
            cpRanges = listOf(1000..1699, 3000..3999, 6000..6999, 11000..11999, 14000..14999),
            addresses = listOf(
                "Paseo de la Reforma #222, Col. Juárez, Cuauhtémoc",
                "Av. Álvaro Obregón #150, Col. Roma Norte, Cuauhtémoc",
                "Av. Insurgentes Sur #800, Col. Del Valle, Benito Juárez",
                "Calle Centenario #45, Col. Villa Coyoacán, Coyoacán",
                "Calle Hamburgo #103, Col. Juárez, Cuauhtémoc",
                "Av. Universidad #1201, Col. Xoco, Benito Juárez",
                "Calle Francisco I. Madero #20, Col. Centro Histórico, Cuauhtémoc",
                "Av. Horacio #340, Col. Polanco V Sección, Miguel Hidalgo"
            )
        ),
        StateConfig(
            name = "Nuevo León",
            codeCURP = "NL",
            ladas = listOf("81", "821", "824", "826"),
            cpRanges = listOf(64000..64999, 66220..66290, 66600..66649, 67100..67190),
            addresses = listOf(
                "Av. Constitución #300, Col. Centro, Monterrey",
                "Calzada del Valle #400, Col. Del Valle, San Pedro Garza García",
                "Av. Eugenio Garza Sada #2501, Col. Tecnológico, Monterrey",
                "Calle Morelos #120, Col. Centro, Monterrey",
                "Av. Lázaro Cárdenas #2400, Col. Valle Oriente, San Pedro Garza García",
                "Calle Paseo de los Leones #1820, Col. Cumbres, Monterrey",
                "Av. Universidad #402, Col. San Nicolás Centro, San Nicolás de los Garza",
                "Calle Zaragoza #55, Col. Centro, Apodaca"
            )
        ),
        StateConfig(
            name = "Estado de México",
            codeCURP = "MC",
            ladas = listOf("722", "55", "56", "729"),
            cpRanges = listOf(50000..50290, 52140..52149, 53000..53999, 54000..54199, 55000..55080),
            addresses = listOf(
                "Av. Paseo Tollocan #500, Col. Universidad, Toluca",
                "Calle Morelos #102, Col. Centro, Metepec",
                "Blvd. Manuel Ávila Camacho #12, Col. Satélite, Naucalpan de Juárez",
                "Av. Central #34, Col. Valle de Anáhuac, Ecatepec de Morelos",
                "Calle Sor Juana Inés de la Cruz #201, Col. Centro, Tlalnepantla",
                "Av. Adolfo López Mateos #88, Col. Metropolitana, Ciudad Nezahualcóyotl",
                "Via Adolfo López Mateos #100, Col. Jacarandas, Tlalnepantla",
                "Av. Benito Juárez #45, Col. San Jerónimo Chicahualco, Metepec"
            )
        ),
        StateConfig(
            name = "Puebla",
            codeCURP = "PL",
            ladas = listOf("222", "238", "244", "248"),
            cpRanges = listOf(72000..72590, 72760..72779, 75700..75790),
            addresses = listOf(
                "Av. Don Juan de Palafox y Mendoza #14, Col. Centro, Puebla",
                "Calle 5 de Mayo #402, Col. Centro, Puebla",
                "Av. de la Reforma #2505, Col. Amor, Puebla",
                "Calle 14 Oriente #1002, Col. Barrio de la Luz, Puebla",
                "Blvd. Atlixcayotl #2200, Col. Reserva Territorial Atlixcayotl, San Andrés Cholula",
                "Av. Manuel Espinosa Yglesias #312, Col. Ladrillera de Benítez, Puebla",
                "Calle Independencia #4, Col. Centro, Tehuacán",
                "Calle Morelos #20, Col. Centro, San Pedro Cholula"
            )
        ),
        StateConfig(
            name = "Veracruz",
            codeCURP = "VZ",
            ladas = listOf("229", "228", "271", "272", "921"),
            cpRanges = listOf(91000..91090, 91700..91899, 94290..94299, 96400..96490),
            addresses = listOf(
                "Av. Independencia #54, Col. Centro, Veracruz",
                "Paseo de la Almeja #10, Col. Costa de Oro, Boca del Río",
                "Calle Enríquez #12, Col. Centro, Xalapa",
                "Av. Diaz Mirón #450, Col. Centro, Veracruz",
                "Calle Zaragoza #201, Col. Centro, Coatzacoalcos",
                "Blvd. Adolfo Ruiz Cortines #120, Col. Fracc. Costa de Oro, Boca del Río",
                "Av. Colón #310, Col. Reforma, Veracruz",
                "Calle Araucarias #88, Col. Indeco Animas, Xalapa"
            )
        ),
        StateConfig(
            name = "Guanajuato",
            codeCURP = "GT",
            ladas = listOf("477", "473", "461", "462", "464"),
            cpRanges = listOf(36000..36090, 37000..37590, 38000..38090, 36500..36590),
            addresses = listOf(
                "Blvd. Adolfo López Mateos #1200, Col. Centro, León",
                "Calle Alonso #25, Col. Centro, Guanajuato",
                "Calle Madero #304, Col. Centro, León",
                "Av. Gral. Ramón Corona #45, Col. Centro, Irapuato",
                "Calle Morelos #102, Col. Centro, Celaya",
                "Paseo de la Presa #99, Col. Paseo de la Presa, Guanajuato",
                "Blvd. Juan Alonso de Torres #2002, Col. Valle del Campestre, León",
                "Calle Zaragoza #312, Col. Centro, Salamanca"
            )
        )
    )

    private val namesMasculine = listOf(
        "Juan", "Jose", "Francisco", "Pedro", "Alejandro", "Manuel", "Luis", "Carlos", "Miguel", "Jorge",
        "Antonio", "Jesus", "Roberto", "Eduardo", "Fernando", "Ricardo", "Daniel", "Raul", "David", "Javier",
        "Sergio", "Alberto", "Arturo", "Alfredo", "Gerardo", "Enrique", "Hugo", "Mauricio", "Oscar", "Gabriel",
        "Angel", "Santiago", "Mateo", "Sebastian", "Leonardo", "Emiliano", "Diego", "Diego Armando", "Adrian"
    )

    private val namesFeminine = listOf(
        "Maria", "Ana", "Guadalupe", "Juana", "Margarita", "Leticia", "Silvia", "Cristina", "Alicia", "Teresa",
        "Patricia", "Alejandra", "Rosa", "Martha", "Yolanda", "Carmen", "Sofia", "Camila", "Valentina", "Isabella",
        "Mariana", "Gabriela", "Elena", "Laura", "Claudia", "Adriana", "Veronica", "Beatriz", "Daniela", "Natalia",
        "Elizabeth", "Gloria", "Luz Maria", "Araceli", "Esthela", "Norma", "Juana Maria", "Ximena", "Andrea"
    )

    private val surnames = listOf(
        "Hernandez", "Garcia", "Martinez", "Lopez", "Gonzalez", "Perez", "Rodriguez", "Sanchez", "Ramirez", "Cruz",
        "Flores", "Gomez", "Diaz", "Reyes", "Morales", "Vazquez", "Jimenez", "Ortiz", "Silva", "Torres",
        "Ruiz", "Chavez", "Herrera", "Medina", "Castro", "Vargas", "Guzman", "Salazar", "Juarez", "Aguilar",
        "Mendoza", "Cabrera", "Alvarez", "Castillo", "Romero", "Pacheco", "Navarro", "Solis", "Luna", "Miranda"
    )

    private fun cleanString(input: String): String {
        return input.uppercase()
            .replace('Á', 'A')
            .replace('É', 'E')
            .replace('Í', 'I')
            .replace('Ó', 'O')
            .replace('Ú', 'U')
            .replace('Ü', 'U')
            .replace('Ñ', 'X') // CURP rules substitute Ñ with X
            .trim()
    }

    private fun getFirstInternalVowel(s: String): Char {
        val cleaned = cleanString(s)
        if (cleaned.length <= 1) return 'X'
        for (i in 1 until cleaned.length) {
            val c = cleaned[i]
            if (c in listOf('A', 'E', 'I', 'O', 'U')) {
                return c
            }
        }
        return 'X'
    }

    private fun getFirstInternalConsonant(s: String): Char {
        val cleaned = cleanString(s)
        if (cleaned.length <= 1) return 'X'
        for (i in 1 until cleaned.length) {
            val c = cleaned[i]
            if (c !in listOf('A', 'E', 'I', 'O', 'U') && c in 'A'..'Z') {
                return c
            }
        }
        return 'X'
    }

    fun generate(selectedStateName: String? = null): GeneratedIdentity {
        val state = if (selectedStateName != null) {
            states.firstOrNull { it.name == selectedStateName } ?: states.random()
        } else {
            states.random()
        }

        val gender = if (Random.nextBoolean()) "H" else "M"
        val firstName = if (gender == "H") namesMasculine.random() else namesFeminine.random()
        val paternal = surnames.random()
        val maternal = surnames.random()

        val birthYear = Random.nextInt(1965, 2005)
        val birthMonth = Random.nextInt(1, 13)
        val birthDay = when (birthMonth) {
            2 -> if (birthYear % 4 == 0) Random.nextInt(1, 30) else Random.nextInt(1, 29)
            4, 6, 9, 11 -> Random.nextInt(1, 31)
            else -> Random.nextInt(1, 32)
        }

        val yearStr = birthYear.toString().takeLast(2)
        val monthStr = String.format("%02d", birthMonth)
        val dayStr = String.format("%02d", birthDay)
        val dobCode = "$yearStr$monthStr$dayStr"

        // CURP Logic
        val pClean = cleanString(paternal)
        val mClean = cleanString(maternal)
        val fClean = cleanString(firstName)

        val p1 = if (pClean.isNotEmpty()) pClean[0] else 'X'
        val p2 = getFirstInternalVowel(pClean)
        val m1 = if (mClean.isNotEmpty()) mClean[0] else 'X'
        val f1 = if (fClean.isNotEmpty()) fClean[0] else 'X'

        val pConsonant = getFirstInternalConsonant(pClean)
        val mConsonant = getFirstInternalConsonant(mClean)
        val fConsonant = getFirstInternalConsonant(fClean)

        val curpState = state.codeCURP
        
        // Alphanumeric homoclave for CURP (differentiator depending on century + check digit)
        val centuryChar = if (birthYear < 2000) "0" else "A"
        val checkDigit = Random.nextInt(0, 10).toString()
        val curp = "$p1$p2$m1$f1$dobCode$gender$curpState$pConsonant$mConsonant$fConsonant$centuryChar$checkDigit"

        // RFC Logic
        val rfcHomoclave = "" + 
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".random() + 
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".random() + 
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".random()
        val rfc = "$p1$p2$m1$f1$dobCode$rfcHomoclave"

        // Telephone LADA based logic
        val lada = state.ladas.random()
        val phoneDigitsCount = if (lada.length == 2) 8 else 7
        val localDigits = (1..phoneDigitsCount).map { Random.nextInt(0, 10) }.joinToString("")
        val formattedPhone = if (lada.length == 2) {
            "($lada) ${localDigits.take(4)}-${localDigits.takeLast(4)}"
        } else {
            "($lada) ${localDigits.take(3)}-${localDigits.takeLast(4)}"
        }

        // State-specific ZIP/CP range
        val range = state.cpRanges.random()
        val cpInt = Random.nextInt(range.first, range.last + 1)
        val postalCode = String.format("%05d", cpInt)

        // Select a sample street and assemble address with CP
        val baseAddress = state.addresses.random()
        val addressFull = "$baseAddress, C.P. $postalCode, ${state.name}"

        return GeneratedIdentity(
            name = "$firstName $paternal $maternal",
            paternalLastName = paternal,
            maternalLastName = maternal,
            firstName = firstName,
            curp = curp.uppercase(),
            rfc = rfc.uppercase(),
            phone = formattedPhone,
            address = addressFull,
            postalCode = postalCode,
            stateName = state.name,
            gender = if (gender == "H") "Masculino" else "Femenino",
            birthDateStr = "$dayStr/$monthStr/$birthYear"
        )
    }
}
