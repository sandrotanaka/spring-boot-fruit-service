/*
 * Copyright 2016-2017 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.snowdrop.example.service;

import dev.snowdrop.example.exception.NotFoundException;
import dev.snowdrop.example.exception.UnprocessableEntityException;
import dev.snowdrop.example.exception.UnsupportedMediaTypeException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import io.micrometer.core.instrument.Metrics;

@RestController
@RequestMapping(value = "/api/fruits")
public class FruitController {

    private final FruitRepository repository;
    private static Integer DELAY_IN_MILLISECONDS = 0;

    public FruitController(FruitRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/delay/{delayInMilliseconds}")
    public String delay(@PathVariable("delayInMilliseconds") Integer delayInMilliseconds) {
        if (delayInMilliseconds < 0 || delayInMilliseconds > 30*1000) {
            return "DELAY_IN_MILLISECONDS not set argument must be > 0 and <= 30";
        }
        DELAY_IN_MILLISECONDS = delayInMilliseconds;        

        return "DELAY_IN_MILLISECONDS set to " + DELAY_IN_MILLISECONDS;
    }

    @GetMapping("/{id}")
    public Fruit get(@PathVariable("id") Integer id) {
        // >>> Prometheus metric
        Metrics.counter("api.http.requests.total", "api", "inventory", "method", "GET", "endpoint", 
            "/inventory/" + id).increment();
        // <<< Prometheus metric
        verifyFruitExists(id);

        timeOut(DELAY_IN_MILLISECONDS);

        return repository.findById(id).get();
    }

    @GetMapping
    public List<Fruit> getAll() {
        // Prometheus metric
        Metrics.counter("api.http.requests.total", "api", "inventory", "method", "GET", "endpoint", 
        "/inventory").increment();
        // <<< Prometheus metric
        Spliterator<Fruit> fruits = repository.findAll()
                .spliterator();

        timeOut(DELAY_IN_MILLISECONDS);

        return StreamSupport
                .stream(fruits, false)
                .collect(Collectors.toList());
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public Fruit post(@RequestBody(required = false) Fruit fruit) {
        verifyCorrectPayload(fruit);

        timeOut(DELAY_IN_MILLISECONDS);

        return repository.save(fruit);
    }

    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/{id}")
    public Fruit put(@PathVariable("id") Integer id, @RequestBody(required = false) Fruit fruit) {
        verifyFruitExists(id);
        verifyCorrectPayload(fruit);

        fruit.setId(id);

        timeOut(DELAY_IN_MILLISECONDS);
        
        return repository.save(fruit);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Integer id) {
        verifyFruitExists(id);

        repository.deleteById(id);
        
        timeOut(DELAY_IN_MILLISECONDS);
    }

    private void verifyFruitExists(Integer id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException(String.format("Fruit with id=%d was not found", id));
        }
    }

    private void verifyCorrectPayload(Fruit fruit) {
        if (Objects.isNull(fruit)) {
            throw new UnsupportedMediaTypeException("Fruit cannot be null");
        }

        if (Objects.isNull(fruit.getName()) || fruit.getName().trim().length() == 0) {
            throw new UnprocessableEntityException("The name is required!");
        }

        if (!Objects.isNull(fruit.getId())) {
            throw new UnprocessableEntityException("Id field must be generated");
        }
    }

    private void timeOut(Integer timeInMillis) {
        try {
			TimeUnit.MILLISECONDS.sleep(DELAY_IN_MILLISECONDS);
		} catch (InterruptedException e) {
		}
    }
}
