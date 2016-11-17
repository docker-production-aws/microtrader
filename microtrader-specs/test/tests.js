/*
 * Variables
 */
var chai = require('chai'),
    should = chai.should,
    expect = chai.expect,
    Promise = require('bluebird'),
    request = require('superagent-promise')(require('superagent'), Promise),
    chaiAsPromised = require('chai-as-promised'),
    chaiJsonSchema = require('chai-json-schema'),
    waitUntil = require('wait-until'),
    EventBus = require('vertx3-eventbus-client'),
    PortfolioService=require('../../microtrader-dashboard/src/main/resources/webroot/libs/portfolio_service-proxy.js');

chai.use(chaiAsPromised).use(chaiJsonSchema);

var quoteUrl = process.env.QUOTE_URL || 'http://localhost:35000/';
var auditUrl = process.env.AUDIT_URL || 'http://localhost:33000/';
var dashboardUrl = process.env.DASHBOARD_URL || 'http://localhost:8000/';
var eventbusUrl = dashboardUrl + 'eventbus/';
var discoveryUrl = dashboardUrl + 'discovery/';
var operationsUrl = dashboardUrl + 'operations/';

var quoteSchema = {
  volume: { type: 'number' },
  shares: { type: 'number' },
  symbol: { type: 'string' },
  name: { type: 'string' },
  ask: { type: 'number' },
  exchange: { type: 'string' },
  bid: { type: 'number' },
  open: { type: 'number' }
}

var tradeSchema = {
  title: 'trade schema',
  type: 'object',
  required: ['action', 'quote', 'date', 'amount', 'owned'],
  properties: {
    action: { type: 'string', enum: ["BUY","SELL"] },
    quote: { 
      type: 'object',
      properties: quoteSchema
    },
    date: { type: 'number' },
    amount: { type: 'number' },
    owned: { type: 'number' }
  }
};

/*
 * Tests
 */

describe('Trader Dashboard Frontend', function() {
  var result;

  before (function() {
    result = get(dashboardUrl)
  })

  it('should return a 200 OK response', function() {
    return assert(result, "status").to.equal(200);
  });
});

describe('Quote Service REST Endpoint', function() {
  var result;

  before (function() {
    result = get(quoteUrl);
  });

  it('should return a 200 OK response', function() {
    return assert(result, "status").to.equal(200);
  });

  it('should return a MacroHard quote', function(done) {
    result.then(function(response) {
      expect(response.body.MacroHard).to.exist;
      expect(response.body.MacroHard).to.have.property("ask");
      done();
    });
  });

  it('should return a Black Coat quote', function(done) {
    result.then(function(response) {
      expect(response.body['Black Coat']).to.exist;
      expect(response.body['Black Coat']).to.have.property("ask");
      done();
    });
  });

  it('should return a Divinator quote', function(done) {
    result.then(function(response) {
      expect(response.body.Divinator).to.exist;
      expect(response.body.Divinator).to.have.property("ask");
      done();
    });
  });
});

describe('Audit Service REST Endpoint', function() {
  var result;

  before (function() {
    result = get(auditUrl)
  })

  it('should return a 200 OK response', function() {
    return assert(result, "status").to.equal(200);
  });

  it('should return an array of stock trades', function(done) {
    result.then(function(response) {
      expect(response.body).to.be.instanceOf(Array);
      expect(response.body).to.have.length.within(0,10);
      done();
    });
  });
});

describe('Audit Operations Endpoint', function() {
  var result;

  before (function() {
    result = get(operationsUrl)
  })

  it('should return a 200 OK response', function() {
    return assert(result, "status").to.equal(200);
  });

  it('should return an array of stock trades', function(done) {
    result.then(function(response) {
      expect(response.body).to.be.instanceOf(Array);
      expect(response.body).to.have.length.within(0,10);
      done();
    });
  });
});

describe('Service Discovery Endpoint', function() {
  var result;

  before (function() {
    result = get(discoveryUrl)
  })

  it('should return a 200 OK response', function() {
    return assert(result, "status").to.equal(200);
  });

  it('should return an array of service location records', function(done) {
    result.then(function(response) {
      expect(response.body).to.be.instanceOf(Array);
      expect(response.body).to.have.length(5);
      done();
    });
  });

  it('should list all services having a status of UP', function(done) {
    result.then(function(response) {
      response.body.forEach(function(item) {
        expect(item.status).to.equal("UP");
      })
      done();
    });
  });
});

describe('Market Events', function() {
  var eventbus;
  var quotes = [];
  
  before (function(done) {
    eventbus = new EventBus(eventbusUrl);
    eventbus.onopen = function () {
      eventbus.registerHandler('market', function (error, message) {
        quotes.push(message.body);
      });
      done();
    };
  });

  it('should receive market data', function(done) {
    this.timeout(60000);
    waitUntil().interval(1000).times(60)
      .condition(function() {
        return quotes.length > 3;
      })
      .done(function(result) {
        expect(quotes.length).to.be.above(3)
        quotes.forEach(function(item) {
          expect(item).to.be.jsonSchema(quoteSchema);
        });
        done()
      });
  });
});

describe('Portfolio Operations', function() {
  var eventbus;
  var trades = [];
  var service;

  before (function(done) {
    eventbus = new EventBus(eventbusUrl);
    service = new PortfolioService(eventbus, "service.portfolio");
    eventbus.onopen = function () {
      eventbus.registerHandler('portfolio', function (error, message) {
        trades.push(message.body);
      });
      done();
    };
  });

  it('should receive portfolio trading events', function(done) {
    this.timeout(60000);
    waitUntil().interval(1000).times(60)
      .condition(function() {
        return trades.length > 0;
      })
      .done(function(result) {
        expect(trades.length).to.be.above(0)
        trades.forEach(function(item) {
          expect(item).to.be.jsonSchema(tradeSchema);
        });
        done();
      });
  });

  it('should retrieve portfolio service data', function(done) {
    service.getPortfolio(function(err,result) {
      expect(result.cash).to.be.above(0)
      done()
    });
  });
});

/*
 * Convenience functions
 */

// POST request with data and return promise
function post(url, data) {
  return request.post(url)
    .set('Content-Type', 'application/json')
    .set('Accept', 'application/json')
    .send(data)
    .end();
}

// GET request and return promise
function get(url) {
  return request.get(url)
    .set('Accept', 'application/json')
    .end();
}

// DELETE request and return promise
function del(url) {
  return request.del(url).end();
}

// UPDATE request with data and return promise
function update(url, method, data) {
  return request(method, url)
    .set('Content-Type', 'application/json')
    .set('Accept', 'application/json')
    .send(data)
    .end();
}

// Resolve promise for property and return expectation
function assert(result, prop) {
  return expect(result).to.eventually.have.deep.property(prop)
}