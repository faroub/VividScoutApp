import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  alias(libs.plugins.detekt)
  jacoco
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.vividscout.trvjxq"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH")
      val storePass = System.getenv("STORE_PASSWORD")
      val alias = System.getenv("KEY_ALIAS")
      val keyPass = System.getenv("KEY_PASSWORD")

      if (!keystorePath.isNullOrBlank()) {
        storeFile = file(keystorePath)
      }
      if (!storePass.isNullOrBlank()) {
        storePassword = storePass
      }
      if (!alias.isNullOrBlank()) {
        keyAlias = alias
      }
      if (!keyPass.isNullOrBlank()) {
        keyPassword = keyPass
      }
    }
    create("debugConfig") {
      // AI Studio convention: debug builds sign with the project-local debug keystore using
      // the well-known standard Android debug credentials (not a secret). The debug build
      // type below only applies this config when such a keystore is actually present.
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      // When AI Studio ships debug.keystore in the repository root it is used as before. When
      // it is absent (fresh clone, CI runner) no signingConfig is assigned, so AGP applies its
      // standard debug signing: the user-level keystore at ~/.android/debug.keystore, which
      // AGP generates automatically on first use.
      if (rootProject.file("debug.keystore").exists()) {
        signingConfig = signingConfigs.getByName("debugConfig")
      }
      // JaCoCo instrumentation for local unit tests: enables AGP's
      // createDebugUnitTestCoverage (JaCoCoReport) task so coverage can be measured
      // without changing any test behavior.
      enableUnitTestCoverage = true
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  sourceSets {
    // Room migration tests (Robolectric) resolve exported schemas through the app's merged
    // assets, so the KSP-exported schema directory is wired into the main source set.
    getByName("main").assets.srcDirs("$projectDir/schemas")
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
  lint {
    checkReleaseBuilds = true
    abortOnError = true
    baseline = file("lint-baseline.xml")
  }
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Static analysis guardrail: detekt runs with the default rule set and suppresses the
// pre-existing findings recorded in config/detekt/baseline.xml, so CI fails only on
// NEW findings. Regenerate the baseline only after intentionally accepting the
// current state: ./gradlew :app:detektBaseline
detekt {
  baseline = file("config/detekt/baseline.xml")
  buildUponDefaultConfig = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
  // detekt's parser predates JVM 25 targets (its CLI accepts up to 22); the project compiles to Java 11.
  jvmTarget = "11"
}
tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
  jvmTarget = "11"
}

// ./gradlew :app:jacocoTestReport — runs the debug unit tests (instrumented by
// enableUnitTestCoverage above) and aggregates their coverage into
// app/build/reports/jacoco/jacocoTestReport/. Kept minimal: it reuses the .exec data
// AGP already produces instead of duplicating instrumentation setup.
tasks.register<JacocoReport>("jacocoTestReport") {
  dependsOn("testDebugUnitTest")
  group = "verification"
  description = "Generates the JaCoCo coverage report for the debug unit tests."
  // The unit tests execute against AGP's bundled runtime classes jar
  // (runtime_app_classes_jar), not the raw classes directory, so the report must
  // analyze that jar to match what the test JVM actually loaded.
  classDirectories.setFrom(
    layout.buildDirectory.file("intermediates/runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar")
  )
  sourceDirectories.setFrom(files("$projectDir/src/main/java"))
  executionData.setFrom(
    layout.buildDirectory.file("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
  )
  reports {
    xml.required.set(true)
    html.required.set(true)
    csv.required.set(false)
    html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/jacocoTestReport/html"))
    xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/jacocoTestReport/jacoco.xml"))
  }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  // Scout Vector Map Engine (Internal Self-Contained Jetpack Compose Vector Engine):
  // Powered by com.example.engine.ScoutVectorEngine & com.example.ui.components.MapCanvasView
  // 100% offline-first vector map rendering, tile geometry projection, and GPS telemetry pre-warming.
  // implementation(libs.maps.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.firestore)

  // Firebase Auth for secure cloud attestation and Firestore writes
  implementation(libs.firebase.auth)
  implementation(libs.firebase.config)
  implementation(libs.firebase.functions)
  implementation(libs.firebase.storage)
  implementation(libs.firebase.appcheck.playintegrity)
  implementation(libs.firebase.appcheck.recaptcha)
  // Debug-only App Check provider: ships exclusively in debug builds (P5). The
  // reference in AppCheckInitializer.loadDebugAppCheckProviderFactory is reflective
  // so release compilation does not resolve this artifact.
  debugImplementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.room.testing)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

gradle.taskGraph.whenReady {
  val hasReleaseTask = allTasks.any { task ->
    val name = task.name
    (name.startsWith("assemble") || name.startsWith("bundle") || name.startsWith("package")) && name.contains("Release")
  }
  if (hasReleaseTask) {
    val keystorePath = System.getenv("KEYSTORE_PATH")
    val storePass = System.getenv("STORE_PASSWORD")
    val alias = System.getenv("KEY_ALIAS")
    val keyPass = System.getenv("KEY_PASSWORD")

    val missingVars = mutableListOf<String>()
    if (keystorePath.isNullOrBlank()) missingVars.add("KEYSTORE_PATH")
    if (storePass.isNullOrBlank()) missingVars.add("STORE_PASSWORD")
    if (alias.isNullOrBlank()) missingVars.add("KEY_ALIAS")
    if (keyPass.isNullOrBlank()) missingVars.add("KEY_PASSWORD")

    if (missingVars.isNotEmpty()) {
      throw GradleException(
        "Release build failed: Missing required environment variable(s) for release signing: " +
        "${missingVars.joinToString(", ")}. " +
        "Please see the 'Release Signing & Environment Variables' section in README.md for instructions on configuring release signing."
      )
    }

    val keystoreFile = file(keystorePath!!)
    if (!keystoreFile.exists()) {
      throw GradleException(
        "Release build failed: Keystore file defined by KEYSTORE_PATH does not exist at '${keystoreFile.absolutePath}'. " +
        "Please see the 'Release Signing & Environment Variables' section in README.md for setup details."
      )
    }
  }
}

