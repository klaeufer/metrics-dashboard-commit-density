# Metrics Dashboard Backend Service

[![Build Status](https://travis-ci.org/sshilpika/metrics-dashboard-commit-density.svg?branch=master)](https://travis-ci.org/sshilpika/metrics-dashboard-commit-density)

## How to use

1. Create a personal access token for GitHub [here](https://github.com/settings/tokens).

1. Store this token in `~/githubAccessToken` and `~/tokens_all`.

1. To ingest the data for a repository, run an instance of the [storage/ingestion script](https://github.com/sshilpika/metrics-dashboard-storage-service) like so. (This really is intended to be a script, even though it is currently called a service.)

        sbt 'runMain edu.luc.cs.metrics.ingestion.service.Boot'
        1. Issues
        2. Commits
        1
        [INFO] [02/26/2018 00:18:23.859] [run-main-0] [package$(akka://gitDefectDensity)] 
        Enter username/reponame/branchname
        klaeufer/meetup-client-scala/master
        [INFO] [02/26/2018 00:18:32.016] [run-main-0] [package$(akka://gitDefectDensity)] You entered: 
        Username: klaeufer 
        Reponame: meetup-client-scala 
        Branchname: master
        [INFO] [02/26/2018 00:18:32.226] [run-main-0] [package$(akka://gitDefectDensity)] Ingestion Service started
        ...
        Success:!!!! Issues for meetup-client-scala  Saved in DB

    and again via

        sbt 'runMain edu.luc.cs.metrics.ingestion.service.Boot'
        1. Issues
        2. Commits
        2
        [INFO] [02/26/2018 00:22:18.974] [run-main-0] [package$(akka://gitDefectDensity)] 
        Enter username/reponame/branchname
        klaeufer/meetup-client-scala/master
        [INFO] [02/26/2018 00:22:25.999] [run-main-0] [package$(akka://gitDefectDensity)] You entered: 
        Username: klaeufer 
        Reponame: meetup-client-scala 
        Branchname: master
        [INFO] [02/26/2018 00:22:26.181] [run-main-0] [package$(akka://gitDefectDensity)] Ingestion Service started
        ...
        Storing tracked Db name for meetup-client-scala

1. Now run an instance of this Metrics Dashboard backend service (this project) via `sbt run` or the script generated by `sbt stage`.

1. Then use an HTTP client to access the desired resources:

        curl -i localhost:8080/density/klaeufer/meetup-client-scala/master\?groupBy=week

        curl -i localhost:8080/spoilage/klaeufer/meetup-client-scala/master\?groupBy=week

    and you should see a bunch of JSON responses (productivity isn't yet implemented).

    If you see

        {
          "defectDensity": {
            "error": "The repository isn't being tracked yet. Please submit a request for tracking the repository"
          }
        }

    then something might have gone wrong when trying to ingest the data for the desired repository.
