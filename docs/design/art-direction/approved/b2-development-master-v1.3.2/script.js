(function(){
  // Categories C3.1 is an approved visual selection only; production filtering remains Backend-owned.
  const selection = document.querySelector('#categories .selection');
  const demoSelected = document.getElementById('selected-category');
  const clear = document.getElementById('clear-filter');
  const cards = [...document.querySelectorAll('#categories button.cat')];
  function choose(name,active){
    cards.forEach(c=>c.setAttribute('aria-pressed',String(c===active)));
    if(selection) selection.textContent = active ? 'Đang xem: '+name : 'Chưa chọn danh mục';
    if(demoSelected) demoSelected.textContent = active ? name : 'Tất cả lĩnh vực';
    if(clear) clear.hidden=!active;
  }
  cards.forEach(c=>c.addEventListener('click',()=>choose(c.dataset.filter,c)));
  clear?.addEventListener('click',()=>choose('',null));

  // Transparency T1.2 kept semantically separate from actual independent verification.
  const trigger=document.getElementById('transparency-more-button');
  const panel=document.getElementById('transparency-detail');
  trigger?.addEventListener('click',()=>{
    const expanded=trigger.getAttribute('aria-expanded')==='true';
    trigger.setAttribute('aria-expanded',String(!expanded));
    panel.hidden=expanded;
    panel.setAttribute('aria-hidden',String(expanded));
    const plus=trigger.querySelector('.plus');
    if(plus)plus.textContent=expanded?'+':'−';
  });
})();
