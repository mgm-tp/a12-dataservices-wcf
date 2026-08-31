pipeline {
	agent {
		label 'linux-node'
	}

    options {
		timestamps()
        ansiColor('xterm')
    }

    environment {
		JAVA_HOME = tool 'OpenJDK21'
        GRADLE_HOME = tool 'Gradle8'
        GRADLE_USER_HOME = "${WORKSPACE}/.gradle"
		PATH = "${env.GRADLE_HOME}/bin:${env.JAVA_HOME}/bin:${env.PATH}"

		ARTIFACTORY_CREDENTIALS = credentials('ci-user-artifactory-token')
		repository_username = "${env.ARTIFACTORY_CREDENTIALS_USR}"
		repository_password = "${env.ARTIFACTORY_CREDENTIALS_PSW}"
		SONAR_TOKEN = credentials('ci-user-sonarqube-token')
    }

    tools {
		jdk 'OpenJDK21'
		gradle 'Gradle8'
	}

	stages {
		stage('Initialization') {
			steps {
				sshagent(credentials: ['ci-user-ssh']) {
					cleanWs()
					checkout scm
					sh 'git config user.name a12-ci'
					sh 'git config user.email a12-infrastructure-guild@mgm-tp.com'
				}
			}
		}

		stage('Set version') {
			when { expression { params.VERSION || env.BUILD_TYPE == 'NIGHTLY' } }
			steps {
				configFileProvider([configFile(fileId: 'gradle-settings', targetLocation: "${env.WORKSPACE}/.gradle/init.d/settings.gradle")]) {
					script {
						def versionToSet = params.VERSION
						if (env.BUILD_TYPE == 'NIGHTLY') {
							def baseVersion = sh(returnStdout: true, script: "grep -E '^version=' gradle.properties | cut -d'=' -f2 | tr -d '\\n'").trim()
							def baseNoSnapshot = baseVersion.replaceAll(/-SNAPSHOT$/, '')
							def buildNumber = env.BUILD_NUMBER ?: '0'
							def shortHash = sh(returnStdout: true, script: "git rev-parse --short HEAD").trim()
							versionToSet = "${baseNoSnapshot}-build.${buildNumber}.${shortHash}"
							echo "Computed NIGHTLY version: ${versionToSet}"
						}
						sh "gradle setVersion -Pversion=${versionToSet}${params.FULL_LOG?' -i -s':''}"
					}
				}
			}
		}

		stage('Build') {
			when { expression { !params.SKIP_TESTS } }
			steps {
				configFileProvider([configFile(fileId: 'gradle-settings', targetLocation: "${env.WORKSPACE}/.gradle/init.d/settings.gradle")]) {
					sh "gradle build${params.FULL_LOG?' -i -s':''}"
				}
			}
		}

		stage('Publish artifacts') {
			when { expression { return !params.DRY_RUN } }
			steps {
				configFileProvider([configFile(fileId: 'gradle-settings', targetLocation: "${env.WORKSPACE}/.gradle/init.d/settings.gradle")]) {
					echo "params: ${params}"
					echo "dry run: ${params.DRY_RUN}"
					sh "gradle publishAllPublicationsToMgm-maven-releasesRepository${params.FULL_LOG?' -i -s':''}"
				}
			}
		}

		stage('Commit and tag release') {
			when { expression { env.BUILD_TYPE == 'RELEASE' && params.VERSION && !params.DRY_RUN } }
			steps {
				sshagent(credentials: ['ci-user-ssh']) {
					sh "git add gradle.properties"
					sh "git commit -m 'NEW_VERSION: ${params.VERSION}'"
					sh "git tag 'A12-${params.VERSION}' -m 'A12-${params.VERSION} Tag'"
					sh "git push origin 'A12-${params.VERSION}'"
				}
			}
		}

		stage('Bump to next version') {
			when { expression { env.BUILD_TYPE == 'RELEASE' && params.NEXT_VERSION && !params.DRY_RUN } }
			steps {
				sshagent(credentials: ['ci-user-ssh']) {
					configFileProvider([configFile(fileId: 'gradle-settings', targetLocation: "${env.WORKSPACE}/.gradle/init.d/settings.gradle")]) {
						script {
							def branchName = params.BRANCH ?: 'master'
							sh "git checkout ${branchName}"
							sh "gradle setVersion -Pversion=${params.NEXT_VERSION}${params.FULL_LOG ? ' -i -s' : ''}"
							sh "git add gradle.properties"
							sh "git commit -m 'Set next version: ${params.NEXT_VERSION}'"
							sh "git push -u origin '${branchName}'"
						}
					}
				}
			}
		}
	}
}
