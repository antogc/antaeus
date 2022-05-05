## Antaeus

Antaeus (/ænˈtiːəs/), in Greek mythology, a giant of Libya, the son of the sea god Poseidon and the Earth goddess Gaia. He compelled all strangers who were passing through the country to wrestle with him. Whenever Antaeus touched the Earth (his mother), his strength was renewed, so that even if thrown to the ground, he was invincible. Heracles, in combat with him, discovered the source of his strength and, lifting him up from Earth, crushed him to death.

Welcome to our challenge.

## The challenge

As most "Software as a Service" (SaaS) companies, Pleo needs to charge a subscription fee every month. Our database contains a few invoices for the different markets in which we operate. Your task is to build the logic that will schedule payment of those invoices on the first of the month. While this may seem simple, there is space for some decisions to be taken and you will be expected to justify them.

## Instructions

Fork this repo with your solution. Ideally, we'd like to see your progression through commits, and don't forget to update the README.md to explain your thought process.

Please let us know how long the challenge takes you. We're not looking for how speedy or lengthy you are. It's just really to give us a clearer idea of what you've produced in the time you decided to take. Feel free to go as big or as small as you want.

## Developing

Requirements:
- \>= Java 11 environment

Open the project using your favorite text editor. If you are using IntelliJ, you can open the `build.gradle.kts` file and it is gonna setup the project in the IDE for you.

### Building

```
./gradlew build
```

### Running

There are 2 options for running Anteus. You either need libsqlite3 or docker. Docker is easier but requires some docker knowledge. We do recommend docker though.

*Running Natively*

Native java with sqlite (requires libsqlite3):

If you use homebrew on MacOS `brew install sqlite`.

```
./gradlew run
```

*Running through docker*

Install docker for your platform

```
docker build -t antaeus
docker run antaeus
```

### App Structure
The code given is structured as follows. Feel free however to modify the structure to fit your needs.
```
├── buildSrc
|  | gradle build scripts and project wide dependency declarations
|  └ src/main/kotlin/utils.kt 
|      Dependencies
|
├── pleo-antaeus-app
|       main() & initialization
|
├── pleo-antaeus-core
|       This is probably where you will introduce most of your new code.
|       Pay attention to the PaymentProvider and BillingService class.
|
├── pleo-antaeus-data
|       Module interfacing with the database. Contains the database 
|       models, mappings and access layer.
|
├── pleo-antaeus-models
|       Definition of the Internal and API models used throughout the
|       application.
|
└── pleo-antaeus-rest
        Entry point for HTTP REST API. This is where the routes are defined.
```

### Main Libraries and dependencies
* [Exposed](https://github.com/JetBrains/Exposed) - DSL for type-safe SQL
* [Javalin](https://javalin.io/) - Simple web framework (for REST)
* [kotlin-logging](https://github.com/MicroUtils/kotlin-logging) - Simple logging framework for Kotlin
* [JUnit 5](https://junit.org/junit5/) - Testing framework
* [Mockk](https://mockk.io/) - Mocking library
* [Sqlite3](https://sqlite.org/index.html) - Database storage engine

Happy hacking 😁!


## Work log

### Initial steps

Initially I just had some basic knowledge about Kotlin, so I decided to take advantage of the challenge to learn Kotlin in depth. 
The first step, before facing the challenge itself, was indeed learning about Kotlin. I spent about 6-8 hours reading the official documentation, 

Next step was reading and understanding the challenge purpose. Once that, I reviewed all the code structure and performed some executions to learn about different components and its interactions.
Even I made a diagram to have a visual about the anteus structure. I spent like on hour on this.

### Initial concerns

During the analysis phase I have identified some key points to be aware of: 
* Scheduled task. Should the application provide a mode to start the process immediately after boot?. In case of an unexpected error during the task execution, we will want to start the process again.
* Billing service basic flow. Two possibilities
  * Two nested loops, first to fetch customers, second to fetch pending invoices by customer. 
  * One loop over all pending invoices (sorted by customerId).
  * I prefer the first one, processing customers in order. This way we will process all the customers pending invoices at the same moment. In case of issues, it will be easier to monitor. Furthermore, we will take advantage of the CustomerNotFoundException skipping remaining invoices.
* Each customer invoice is processed individually. That is, the invoice is charged and then updated in the db (status PAID).
  * What if the updating invoice status process fails? The invoice is already processed but the BD does not reflect the status. An initial idea could be implementing a new service that stores those failures to be re-processed again in some moment (like a `dead letter queue`)
* Assuming a huge amount of Customers, a `pagination` mechanism to get customers would be great.
* Asynchronous implementation. Different customers could be processed in an Asynchronous way.
* Error handling. 
  * NetworkException: retry indefinitely
  * Customer not found exception: log and pass the next customer
  * CurrencyMismatchException: log and pass the next invoice 

### Initial prototype

Having in mind the concerns above-mentioned, I made some decisions for the initial prototype:
* Scheduled task. I had a look at Krontab, and it seemed easy to implement, but I decided to postpone its implementation to focus on the billing service logic.
* Billing service initial implementation. 
  * Two nested loops over customer and customer-invoices.
  * Each invoice is processed and updated in the DB atomically.
  * Error handling with retry for NetworkExceptions
  * No pagination mechanism.
  * No concurrency.
  * Errors on update invoice operation are just logged.

Time spent initial prototype, analysis, design, implementation and tests: 6 hours

### Second evolution

The main idea for the next evolution is to implement the billing process in a concurrent way. The idea is to 
have two different process running at the same time: 
* A process fetching customers from the db.
* Another process in charge of processing customer's invoices.

For that purpose, I will implement a `producer-consumer` pattern using a `Channel producer`. 

The first component will be the `Channel Producer`, that is going to provide a channel serving customers. 
The producer will use a new component provided by the customer service, a `customerPageFetcher`, that component is able to 
get customers pages by page, using `keyset pagination`,  and then the producer will serve customers one by one.

The second component, the `Consumer`, will be in charge of processing invoices for a specific customer, and it's going to use a coroutine for each user.  

Two important questions:
* Why Keyset pagination? Because it provides better performance than regular offset pagination.
* Why pagination for customers and no for invoices?. Looking at the initial data it seems to me that 
the number of customers could be large enough to require a pagination mechanism, I'm not sure the same can happen for invoices.
Assuming a regular behaviour where invoices are processed periodically, I would not expect too much growth.

Time spent: 6 hours

### Improvements

#### Notification service

In systems like Anteus, where transactions (payments in that case) are capital, is always a good idea to keep an event log, 
where all events in the systems are registered. Furthermore, the system should be able to proceed depending on the kind of event, 
for instance:
* by sending an email to customer when an invoice has been charged
* by raising an alert in case of error. 

Think of the case where an Invoice is charged but due to an error in the db the status is not updated. In that case, the notification 
service should alert system administrators, who should fix the issue, in that case, by updating manually the invoice.
Given that the process is going to be executed once per month, it should be enough time to fix all the issues. Anyway, we have 
to be aware that we speak about a manual process, and hence, error prone. If the payment provider can not ensure that the
same invoice can not be charged twice, new countermeasures we be implemented.  

I have provided just a dummy implementation for the notification service, as I consider is out of the challenge purpose. 
Regarding its real design, a few ideas: 
* I would consider using a NO SQL db to store events logs. 
* Furthermore, we could take advantage of the service to generate metrics about the system. Those metrics could be gathered by a monitoring system, for instance Prometheus + Grafana

Time spent: 2 hours



Implement scheduled task. Idea 2 tipos:
* scheduled
  - just once

*Provide extra endpoints
  - update invoice
  - update customer
  - pay invoice
  - list customer pendings

* Provide configuration file, configurationService  as a file of constants.













