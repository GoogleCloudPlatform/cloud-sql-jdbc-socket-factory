# Contributing

1. **Please sign one of the contributor license agreements below!**
1. Fork the repo, develop and test your code changes, add docs.
1. Make sure that your commit messages clearly describe the changes.
1. Send a pull request.

## Table of contents
* [Community Guidelines](#community-guidelines)
* [Contributor License Agreement](#contributor-license-agreement)
* [Opening an issue](#opening-an-issue)
* [Running tests](#running-tests)
* [Code reviews](#code-reviews)


## Community Guidelines

This project follows [Google's Open Source Community
Guidelines](https://opensource.google/conduct/).

## Contributor License Agreement

Contributions to this project must be accompanied by a Contributor License
Agreement. You (or your employer) retain the copyright to your contribution;
this simply gives us permission to use and redistribute your contributions as
part of the project. Head over to <https://cla.developers.google.com/> to see
your current agreements on file or to sign a new one.

You generally only need to submit a CLA once, so if you've already submitted one
(even if it was for a different project), you probably don't need to do it
again.


## Opening an issue

If you find a bug in the proxy code or an inaccuracy in the documentation,
please open an issue. GitHub provides a guide, [Mastering
Issues](https://guides.github.com/features/issues/), that is useful if you are
unfamiliar with the process. Here are the specific steps for opening an issue:

1. Go to the project issues page on GitHub.
1. Click the green `New Issue` button located in the upper right corner.
1. In the title field, write a single phrase that identifies your issue.
1. In the main editor, describe your issue.
1. Click the submit button.

Thank you. We will do our best to triage your issue within one business day, and
attempt to categorize your issues with an estimate of the priority and issue
type. We will try to respond with regular updates based on its priority:

* **Critical** respond and update daily, resolve with a week
* **High** respond and update weekly, resolve within six weeks
* **Medium** respond and update every three months, best effort resolution
* **Low** respond and update every six months, best effort resolution

The priority we assign will be roughly a function of the number of users we
expect to be impacted, as well as its severity. As a rule of thumb:

<table>
  <thead>
    <tr>
      <th rowspan="2">Severity</th>
      <th colspan="4">Number of users</th>
    </tr>
    <tr>
      <th>Handful</th>
      <th>Some</th>
      <th>Most</th>
      <th>All</th>
    </tr>
  </thead>
  <tr>
    <td>Easy, obvious workaround</td>
    <td>Low</td>
    <td>Low</td>
    <td>Medium</td>
    <td>High
  </tr>
  <tr>
<td>Non-obvious workaround available</td>
<td>Low</td>
<td>Medium</td>
<td>High</td>
<td>Critical</td>
  </tr>

  <tr>
<td>Functionality blocked</td>
<td>High</td>
<td>High</td>
<td>Critical</td>
<td>Critical</td>
  </tr>
</table>


### Running tests

The test suite contains both unit tests and integration tests.
Integration tests are skipped by default. To run only unit tests, run
```
mvn clean verify
```
*Note*: The unit tests currently fail to run on Mac OS. Pull Requests will run these tests and as a result they may be skipped locally if necessary.

Before running integration tests, it is necessary to set some environment variables for database credentials. A sample `.envrc.example` file is included in the root directory which documents
which environment variables must be set to run the integration tests. Copy this
example file to `.envrc` at the root of the project, supply all the correct
values for each variable, source the file (`source .envrc`, or consider using
[direnv][]), and then run:

```
mvn clean verify -P e2e 
```

## Code Reviews

All submissions, including submissions by project members, require review. We
use GitHub pull requests for this purpose. Consult
[GitHub Help](https://help.github.com/articles/about-pull-requests/) for more
information on using pull requests.
