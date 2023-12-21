const f = () => {
  let bttBtn = document.querySelector('.btt img');
  
  window.onload = () => {
    bttBtn.style.display = 'none';
  };
  // Window Load
  
  window.onscroll = () => {
    if (document.body.scrollTop > 120 || document.documentElement.scrollTop > 120) {
      document.querySelector(".webpage-header").style.padding = "6px 0";
      document.querySelector(".webpage-logo").style.width = "180px";
    } else {
      document.querySelector(".webpage-header").style.padding = "8px 0";
      document.querySelector(".webpage-logo").style.width = "200px";
    }
  
    if(document.body.scrollTop > 200 || document.documentElement.scrollTop > 200) {
      bttBtn.style.display = 'block';
    }else {
      bttBtn.style.display = 'none';
    }
  };
  // Window Scroll

  const btt = () => {
    bttBtn.addEventListener('click', () => {
      document.body.scrollTop = 0;
      document.documentElement.scrollTop = 0;
    });
  }
  btt();
  // BackToTop

  const responsiveNav = () => {
    let bar = document.querySelector('.solid-bar'),
      close = document.querySelector('.close'),
      navList = document.querySelector('.nav-list'),
      header = document.querySelector('.header-nav');

    bar.addEventListener("click", () => {
      if (navList.style.display === 'block') {
        navList.style.display = 'none';
        header.classList.remove("collapsed");
      }else {
        navList.style.display = 'block';
        header.classList.add("collapsed");
      }
    });

    close.addEventListener('click', () => {
      navList.style.display = 'none';
      header.classList.remove("collapsed");
    });
  }
  responsiveNav();
  // Responsive Menu
};
f();

