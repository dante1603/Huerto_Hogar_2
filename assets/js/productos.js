const contenedor = 
document.getElementById("lista-productos");

const plantilla =
document.getElementById("plantilla-producto");

//frutas    
    const productos = [
        {
        nombre: "Manzana",
        imagen: "assets/img/manzana.jpg",
        descripcion: "Manzanas rojas de campo",
        precio: "10k"
    },
    {
        nombre: "Naranja",
        imagen:"assets/img/naranja.jpg",
        descripcion: "Naranjas dulces, mas dulces que la miel",
        precio: "15k"
    },
    {
        nombre: "Platano verde",
        imagen:"assets/img/platano.jpg",
        descripcion: "Platanos verdes de 45cm",
        precio: "90k"
    },
//verduras
    {
        nombre: "Zanahoria",
        imagen:"assets/img/zanahoria.jpg",
        descripcion: "Organicas y veganas 100%RealnoFake",
        precio: "1k"
    },
    {
        nombre: "Espinaca",
        imagen:"assets/img/espinaca.jpg",
        descripcion: "Hojas comestibles con mucho hierro",
        precio: "8k"
    },
    {
        nombre: "Pimenton",
        imagen:"assets/img/pimiento.jpg",
        descripcion: "Naturales y crugientes, a los ninos nunca les gusta",
        precio: "12k"
    },
    {
        nombre: "Quinua",
        imagen:"assets/img/quinua.jpg",
        descripcion: "Semillas que se expanden en tu boca, se sienten muy raro pero son nutritivas",
        precio: "25k"
    },
    {
        nombre: "Miel",
        imagen:"assets/img/miel.jpg",
        descripcion: "Nunca se descompone, es dorada y saludable",
        precio: "100k"
    },
    {
        nombre: "Leche",
        imagen: "assets/img/leche.jpg",
        descripcion: "Leche de toro contento",
        precio: "40k"
    }
]
    

productos.forEach(producto => {

const tarjeta =
plantilla.content.cloneNode(true);

tarjeta.querySelector(".nombre")
.textContent = producto.nombre;

tarjeta.querySelector(".imagen")
.src = producto.imagen;

tarjeta.querySelector(".descripcion")
.textContent = producto.descripcion;

tarjeta.querySelector(".precio")
.textContent = producto.precio;

    contenedor .appendChild(tarjeta);
});