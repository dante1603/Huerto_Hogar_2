const contenedor = 
document.getElementById("lista-productos");

const plantilla =
document.getElementById("plantilla-producto");

//frutas    
    const productos = [
        {
        nombre: "Manzanas Fuji",
        imagen: "assets/img/manzana.jpg",
        descripcion: "Manzanas rojas de campo",
        precio: " $1,200 CLP / kilo - Stock: 150 kilos"
    },
    {
        nombre: "Naranjas Valencia",
        imagen:"assets/img/naranja.jpg",
        descripcion: "Naranjas dulces, mas dulces que la miel",
        precio: " $1,000 CLP / kilo - Stock: 200 kilos"
    },
    {
        nombre: "Plátanos Cavendish",
        imagen:"assets/img/platano.jpg",
        descripcion: "Plátanos maduros y dulces, perfectos para el desayuno o como snack energético. ",
        precio: "$800 CLP / kilo - Stock: 250 kilos",
    },
//verduras
    {
        nombre: "Zanahorias",
        imagen:"assets/img/zanahoria.jpg",
        descripcion: "Zanahorias crujientes cultivadas sin pesticidas en la Región de O'Higgins",
        precio: "$900 CLP / kilo Stock: 100 kilos"
    },
    {
        nombre: "Espinacas Frescas",
        imagen:"assets/img/espinaca.jpg",
        descripcion: "Hojas comestibles con mucho hierro",
        precio: " $700 CLP / bolsa de 500g - Stock: 80 bolsas"
    },
    {
        nombre: "Pimenton",
        imagen:"assets/img/pimiento.jpg",
        descripcion: "Pimientos rojos, amarillos y verdes, ideales para salteados y platos coloridos.",
        precio: "$1,500 CLP / kilo - stock: 120 kilos"
    },
    {
        nombre: "Miel",
        imagen:"assets/img/miel.jpg",
        descripcion: "Miel pura y orgánica producida por apicultores locales.",
        precio: " $5,000 CLP / frasco de 500g - Stock: 50 frascos"
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