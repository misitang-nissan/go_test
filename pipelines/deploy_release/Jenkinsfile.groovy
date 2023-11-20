#!/usr/bin/env groovy
library('nc-pipeline-lib')

def download_url
pipeline{
    agent {
      label 'backend-agent'
    }
    options {
        buildDiscarder(logRotator(numToKeepStr: '5', artifactNumToKeepStr: '5'))
        skipDefaultCheckout()
    }
    parameters {
        choice choices: ['nissan-ngx-bff'], description: 'app name', name: 'APP'
        choice choices: ['', 'bedev', 'development', 'integration', 'staging', 'preprod', 'prod'],
                description: 'Deploy SPACES on PCF JP2 / JP-STAGING', name: 'BUILDING_SPACE'
        string defaultValue: 'release/1.x', description: 'Branch to clone repository', name: 'BRANCH', trim: false
        string defaultValue: '', description: 'optional \n Tag version to checkout', name: 'TAG_VERSION', trim: false
        string defaultValue: '', description: 'optional \n Name for deploying to PCF', name: 'SPECIFIED_APP_NAME', trim: false
    }
    environment {
        manifestPath = "./BOOT-INF/classes/manifest.yml"
        teamDomain = 'acv-japan'
        specificChannel = "#cd-jp-${params.BUILDING_SPACE}"
        appVersion = ''
        appName = "${params.APP}"
        envPath = 'jp'
    }
    stages {
      stage('Get release binary asset download url') {
        steps {
          script {
              def list_res = httpRequest url: "https://api.github.com/repos/$repository/releases?per_page=30&page=1",
              httpMode: "GET",
              customHeaders: [[name: "Authorization", value: "Bearer $github_token"], [name: "Accept", value: "application/vnd.github+json"], [name: "X-GitHub-Api-Version", value: "$github_api_version"]]
              def list_res_content = readJSON text: list_res.content
              if (list_res.status == 200){
                  for(item in list_res_content) {
                      if ("$version_tag" == item.tag_name) {
                          for(asset in item.assets) {
                              if ("$download_artifact_name" == asset.name){
                                  download_url = asset.url
                                  break
                              }
                          }
                      }
                  }
              }
          }
        }
      }
      stage('Download') {
          steps {
              script {
                if (download_url == null) {
                    println 'download url is not found!'
                }
                httpRequest url: download_url,
                httpMode: "GET", outputFile: "$download_artifact_name",
                customHeaders: [[name: "Authorization", value: "Bearer $github_token"], [name: "Accept", value: "application/octet-stream"], [name: "X-GitHub-Api-Version", value: "$github_api_version"]]
              }
              sh 'file $download_artifact_name'
          }
      }
      stage('Unzip downloaded file') {
          steps {
            //  unzip zipFile: "$download_artifact_name"
            sh 'tar -xzvf "$download_artifact_name"'
          }
      }
    }
    post {
      cleanup {
        sh 'rm -rf '.concat("$download_artifact_name")
      }
    }
}