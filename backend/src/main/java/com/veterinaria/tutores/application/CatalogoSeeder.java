package com.veterinaria.tutores.application;

import java.util.List;
import java.util.Map;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.tutores.domain.Especie;
import com.veterinaria.tutores.domain.Raza;
import com.veterinaria.tutores.internal.EspecieRepository;
import com.veterinaria.tutores.internal.RazaRepository;

/** Siembra especies y razas basicas de forma idempotente ({@code vet.catalogos.seed}). */
@Component
@ConditionalOnProperty(name = "vet.catalogos.seed", havingValue = "true")
public class CatalogoSeeder implements CommandLineRunner {

    private static final List<String> ESPECIES = List.of("Perro", "Gato", "Conejo", "Ave", "Otro");

    private static final Map<String, List<String>> RAZAS = Map.of(
            "Perro", List.of("Criollo", "Labrador Retriever", "Poodle", "Pastor Aleman"),
            "Gato", List.of("Criollo", "Persa", "Siames"));

    private final EspecieRepository especieRepository;
    private final RazaRepository razaRepository;

    public CatalogoSeeder(EspecieRepository especieRepository, RazaRepository razaRepository) {
        this.especieRepository = especieRepository;
        this.razaRepository = razaRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        for (String nombre : ESPECIES) {
            Especie especie = especieRepository.findByNombre(nombre).orElseGet(() -> crearEspecie(nombre));
            sembrarRazas(especie);
        }
    }

    private Especie crearEspecie(String nombre) {
        Especie especie = new Especie();
        especie.setNombre(nombre);
        return especieRepository.save(especie);
    }

    private void sembrarRazas(Especie especie) {
        for (String nombre : RAZAS.getOrDefault(especie.getNombre(), List.of())) {
            if (!razaRepository.existsByEspecieIdAndNombreIgnoreCase(especie.getId(), nombre)) {
                Raza raza = new Raza();
                raza.setEspecie(especie);
                raza.setNombre(nombre);
                razaRepository.save(raza);
            }
        }
    }
}
