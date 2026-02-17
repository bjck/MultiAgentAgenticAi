document.addEventListener('DOMContentLoaded', () => {
    const htmlElement = document.documentElement; // This gets the <html> element
    const themeToggleBtn = document.getElementById('theme-toggle'); // Assuming a button with this ID

    // Check for saved theme preference, default to 'light' if none
    const currentTheme = localStorage.getItem('theme') || 'light';
    htmlElement.setAttribute('data-theme', currentTheme);

    if (themeToggleBtn) {
        themeToggleBtn.addEventListener('click', () => {
            let newTheme = htmlElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
            htmlElement.setAttribute('data-theme', newTheme);
            localStorage.setItem('theme', newTheme);
        });
    }
});
