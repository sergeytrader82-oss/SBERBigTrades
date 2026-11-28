
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ru.sberbigtrades"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.sberbigtrades"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.6")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material:1.7.6")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

// Автоматическая загрузка сертификатов Т-Банка
val downloadTbankCertificates by tasks.registering {

    doLast {
        val rawDirectory =
            layout.projectDirectory.dir("src/main/res/raw").asFile

        rawDirectory.mkdirs()

        val certificates = linkedMapOf(
            "russian_trusted_root_ca.cer" to
                "https://gu-st.ru/content/downloads/Russian_Trusted_Root_CA.cer",

            "russian_trusted_sub_ca.cer" to
                "https://gu-st.ru/content/downloads/Russian_Trusted_Sub_CA.cer",

            "russian_trusted_sub_ca_2024.cer" to
                "https://gu-st.ru/content/downloads/Russian_Trusted_Sub_CA_2024.cer"
        )

        certificates.forEach { (fileName, url) ->

            println("Downloading: $fileName")

            val connection = java.net.URL(url).openConnection().apply {
                connectTimeout = 20000
                readTimeout = 20000
            }

            val bytes = connection.getInputStream().use {
                it.readBytes()
            }

            check(bytes.isNotEmpty()) {
                "ERROR: Empty certificate: $fileName"
            }

            val certificate =
                java.security.cert.CertificateFactory
                    .getInstance("X.509")
                    .generateCertificate(
                        java.io.ByteArrayInputStream(bytes)
                    ) as java.security.cert.X509Certificate

            check(certificate.basicConstraints >= 0) {
                "ERROR: Certificate is not a CA: $fileName"
            }

            val destination = rawDirectory.resolve(fileName)
            destination.writeBytes(bytes)

            println("Certificate OK: ${certificate.subjectX500Principal}")
            println("Saved: ${destination.absolutePath}")
        }

        println("All T-Bank certificates downloaded and checked.")
    }
}

tasks.named("preBuild").configure {
    dependsOn(downloadTbankCertificates)
}
