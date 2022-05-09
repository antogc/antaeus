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
The first step, before facing the challenge itself, was indeed learning about Kotlin by reading the official documentation and performing some hand-ons.
I spent about 6-8 hours.

Next step was reading and understanding the challenge purpose. Once that, I reviewed all the code structure and performed some test executions 
to learn about different components and its interactions. Even I made a diagram to have a visual about the anteus structure. It took me like on hour.

### Initial concerns

During the analysis phase I have identified some key points to be aware of: 

#### Scheduled task 
Should the application provide a mode to start the process immediately after boot?. 
In case of an unexpected error during the task execution, we could want to execute again the process.

#### Billing service basic flow
Initially, I see to possibilities: 
* Two nested loops; first one to fetch customers, and the second one to fetch pending invoices by customer.
* One loop over all pending invoices.

I have decided implementing the first option. Apparently only one invoice should be pending to be charged per customer, but
it is not said that a customer could have more than one. Furthermore, we have to be aware the number of customers can grow up in a huge magnitude.
Iterating first by customers and then by invoices, allows the process
to be tidier. In case of issues, it will be easier to monitor. Furthermore, we will take advantage of the CustomerNotFoundException skipping remaining customer's invoices.  

#### Invoice processing: charge and update
Once an invoice has been charged, we have to update it status in the database. It is a critical moment on the flow, and 
I have decided to implement it in an atomic way, that is, once the invoice is charged, it will be updated in the database.
We could think in more efficient way by using batches to update the status, but in case of errors it will be harder to deal with. 

In case of error on the updating, the system should raise an alert. Then, the issue could be fixed manually or event automatically by using 
a mechanism to re-process failures (like a `dead letter queue`). Anyway, whatever the mechanism, ensuring that a payment is not going to be charge twice is complex, 
manual processes are error-prone, and with an automatic process we would need a high level of sync. 
One way to reduce the risk would be by ensuring that the PaymentProvider can not charge twice the same invoice.

#### Error handling
The Payment provider can raise several exceptions, the billing service will proceed in a different way depends on it:
* NetworkException: I consider that exception like a Recoverable issue, at some moment the connection has to be fixed and the system can continue with the process. I will implement a retry mechanism, based on a backoff algorithm that will re-attempt indefinitely.
* CustomerNotFoundException: in that case the service will log the error and pass the next customer.
* CurrencyMismatchException: I assume that case is because of an error on data,that should be fixed manually. The service will log the issue and passing to the next invoice.

#### Efficiency and performance
Initially I have detected two main aspects to improve the service performance.

<ins>Asynchronous processing</ins>

The service will use a third-party component (Payment provider), and that call could some take time. Using an asynchronous 
implementation we will take advantage on that idle time by performing new calls (or whatever action).  

<ins>Keyset pagination</ins>

We have to assume the number of customers can grow up on a huge magnitude. The service will iterate first over all the customers
and then over its pending invoices. Using a pagination mechanism to fetch customers will avoid the communication or the database to be overloaded. 

Keyset pagination offers better performance than regular offset pagination, specially on large datasets. It is designed to
iterate a whole table, but it can not get specific pages.

### A bit about my coding style

I like to code thinking on several clean-codes principles like SOLID, TDD, KISS. Aside of the solution efficiency and performance, 
the code should serve as a documentation.

I usually start with some basic tests, covering the main acceptance criteria, then I like to implement a basic solution to cover 
the tests. Once we have the security that tests provide I start by applying refactors on the solution, initially to provide 
performance, and finally to improve the code quality. 

### Initial prototype

Having in mind the concerns above-mentioned, I made some decisions for the initial prototype:
* Scheduled task. I had a look at Crontab, and it seemed easy to implement, but I decided to postpone its implementation to focus on the billing service logic.
* Billing service logic. Two nested loops, first iterating over customers and then over customer's pending invoices. No concurrency. No pagination.
* Error handling with retry for NetworkExceptions
* Errors on database updates (from PENDING TO PAID) are just logged.
* Unit test using kotest-assertions-core library.

Time spent initial prototype, analysis, design, implementation and tests: 6 hours

### Second evolution

The main point for the next evolution is to implement the billing process in a concurrent way. The idea is to 
have two different process running at the same time: 
* A process fetching customers from the db (using keyset pagination).
* Another process in charge of processing customer's invoices.

For that purpose, I will implement a `producer-consumer` pattern using a `Channel producer`. 

The first component will be the Channel Producer, that is going to provide a channel serving customers. 
The producer will use a new component provided by the customer service, a `CustomerPageFetcher`, that component is able to 
get customers page by page, using keyset pagination. The producer will send customers one by one.

On the other side, the consumer, I will implement a coroutine that will be in charge of processing customer's pending invoices.   

That mechanism allows decoupling both processes fetching customer from the database and the process of the invoices.
Since the producer will be faster than the consumer, I have introduced a limit on the Channel size to prevent the producer from overloading it. 

Time spent: 6 hours

### Final version

#### Scheduled task
I have implemented a new component that will be in charge of the scheduling of the process. 

The component accepts some configurations: 
* Schedule expression: the task will be launched on a monthly basis, at first day on every month. 
* Execute on boot: it allows to execute the billing process once the app boots. 
* Initial execution delay: in case of execution on boot, the delay in seconds to start after the app is booted. 

#### Notification service

In systems like Anteus, where transactions (payments in that case) are critical, it is always a good idea to keep an event log,
where all events in the systems are registered, those events could be re-processed to fix possible issues. 
Furthermore, the notification service will proceed depending on the kind of event, for instance:
* by sending an email to customer when an invoice has been charged
* by raising an alert in case of error.

Given that all the events are processed for the notification service is good point to generate system metrics.

Due to time limitations I have simply implemented a basic solution, to provide an idea about it.

#### ConfigProvider

Ideally, system configs should be provided by a central component, something similar to what Spring Cloud Config provides. 

Due to time limitations I have not implemented a specific component, I  simply put some constants on a ConfigProvider to serve as a central for configurations.

#### Extra endpoints

I have added some extra endpoints to the api-rest component. The idea is to provide the user more functional ways to manage with the system.
For a real production-ready system, we should provide some auth mechanism to prevent undesired users to use it.

* GET /rest/v1/invoices: get all invoices
* GET /rest/v1/invoices/{:id}: get an specific invoice
* GET /rest/v1/invoices/{:status}: get all invoices of a specific status
* GET /rest/v1/customers: fetch all customers
* GET /rest/v1/customers/{:id}: get a specific customer
* POST /rest/v1/payments/executeBillingProcess: launch the billing process. It will do nothing if the process is already running.
* POST /rest/v1/payments/invoices/{:id}: process an specific invoice

In order to prevent the billing process to be executed twice, I have added a lock that avoids executing the process if it is already running.

### Future improvements

Finally, simply comment some improvements for the future:

<ins> Monitoring & Metrics </ins>

In systems like Anteus is capital to be able to monitor what is happening. Improving the observability helps the system administrator to anticipate future issues.

For that purpose is crucial providing good logging to understand service behaviour. We could also add a mechanism to trace request to the payment provider, 
this way we could match issues on both components.

As above-mentioned, the notification services could be improved to generate system metrics, like: 
* number of invoices processed
* Payment provider call execution time
* Number of errors.

Logs could be processed by a stack like ELK (ElasticSearch, Logstash and Kibana).

For metrics, a system based on Prometheus and Grafana.

<ins> Circuit breaker </ins>

The Payment provider is an external service that could be overloaded by our system. 
To protect it, we could add a circuit breaker.

<ins> Code quality </ins>

Keeping the code quality is capital for whatever development process. 
I would suggest using tools to check the code quality, like a linter for local development and sonar for full analyses.

Time spent: 6 hours

### Conclusions

I have to recognize that I have enjoyed it a lot, but it's been challenging. 

On one side, programming with Kotlin, such a really great language! Initially I barely knew some basic concepts, 
but now I strongly want to continue the travel of becoming an expert in kotlin!. 

On the other side, the business context, I have never worked before with processes related with payments, 
it is not easy providing a secure context for that kind of processes. For the challenge I have tried to provide a 
close-to-real solution but inside the context of a challenge. I have relied on my experience to provide an effective solution. I hope I got it.

Regarding the readme, just to mention that I have tried to translate the whole process since I started with the Kotlin documentation 
until the final phase. I hope it is not too heavy for the reader.

Finally, a summary of the time spent for the challenge:
* Learning Kotlin: 6-8 hours
* Analysis and design: 2 hours
* Initial prototype: 6 hours
* Second evolution: asynchronous and pagination: 6 hours
* Final improvements: 4 hours. 














